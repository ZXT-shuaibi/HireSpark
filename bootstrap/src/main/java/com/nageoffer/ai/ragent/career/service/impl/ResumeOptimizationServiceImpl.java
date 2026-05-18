/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerProgressEventVO;
import com.nageoffer.ai.ragent.career.controller.request.CareerOptimizationCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerSuggestionDecisionRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationSuggestionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationTaskVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeVersionVO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerProgressEventDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationReviewDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationSuggestionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationTaskDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerProgressEventMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationReviewMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationSuggestionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationTaskMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.enums.CareerTaskStatus;
import com.nageoffer.ai.ragent.career.enums.OptimizationReviewStatus;
import com.nageoffer.ai.ragent.career.enums.ResumeSuggestionStatus;
import com.nageoffer.ai.ragent.career.service.ResumeOptimizationService;
import com.nageoffer.ai.ragent.career.service.ResumeOptimizationReviewEvaluator;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancementService;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ResumeOptimizationServiceImpl implements ResumeOptimizationService {

    private static final String SOURCE_TYPE_OPTIMIZED = "OPTIMIZED";
    private static final String DEFAULT_OPTIMIZED_TITLE = "Optimized Resume";
    private static final int CATEGORY_MAX_LENGTH = 64;
    private static final int TITLE_MAX_LENGTH = 128;
    private static final int RISK_LEVEL_MAX_LENGTH = 32;
    private static final int TASK_ERROR_MAX_LENGTH = 1000;
    private static final int EVENT_MESSAGE_MAX_LENGTH = 512;
    private static final int MAX_OPTIMIZATION_ITERATIONS = 3;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ResumeOptimizationTaskMapper taskMapper;
    private final ResumeOptimizationSuggestionMapper suggestionMapper;
    private final ResumeOptimizationReviewMapper reviewMapper;
    private final CareerProgressEventMapper progressEventMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobAlignmentReportMapper alignmentReportMapper;
    private final CareerJsonParser careerJsonParser;
    private final CareerSingleFlightLlmService singleFlightLlmService;
    private final CareerRetrievalEnhancementService careerRetrievalEnhancementService;
    private final ResumeOptimizationReviewEvaluator reviewEvaluator = new ResumeOptimizationReviewEvaluator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建简历优化任务，并通过执行者-裁判多轮迭代完成质量门禁。
     */
    @Override
    public CareerOptimizationTaskVO createTask(CareerOptimizationCreateRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Optimization create request is required");
        }
        if (StrUtil.isBlank(request.getResumeVersionId())) {
            throw new ClientException("Resume version id is required");
        }
        if (StrUtil.isBlank(request.getJdId())) {
            throw new ClientException("Job description id is required");
        }

        ResumeVersionDO resumeVersion = requireResumeVersion(request.getResumeVersionId(), userId);
        String jdId = trimToNull(request.getJdId());
        JobAlignmentReportDO alignmentReport = null;
        if (StrUtil.isNotBlank(request.getAlignmentReportId())) {
            alignmentReport = requireAlignmentReport(request.getAlignmentReportId(), userId);
            ensureAlignmentMatchesRequest(alignmentReport, resumeVersion.getId(), jdId);
            jdId = alignmentReport.getJdId();
        }
        JobDescriptionDO job = jdId == null ? null : requireJob(jdId, userId);

        ResumeOptimizationTaskDO task = ResumeOptimizationTaskDO.builder()
                .userId(userId)
                .resumeVersionId(resumeVersion.getId())
                .jdId(jdId)
                .status(CareerTaskStatus.RUNNING.name())
                .inputJson(buildInputJson(resumeVersion, job, alignmentReport))
                .traceId(null)
                .errorMessage(null)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        taskMapper.insert(task);
        String traceId = "career-opt-" + task.getId();
        task.setTraceId(traceId);

        try {
            CareerRetrievalEnhancement retrievalEnhancement =
                    careerRetrievalEnhancementService.enhanceOptimization(resumeVersion, job,
                            alignmentReport == null ? "{}" : writeJson(buildAlignmentReportPayload(alignmentReport),
                                    "Failed to serialize alignment report JSON"));
            List<CareerProgressEventDO> progressEvents = new ArrayList<>();
            OptimizationIterationResult iterationResult = runOptimizationIterations(
                    task, userId, resumeVersion, job, alignmentReport, retrievalEnhancement, progressEvents);
            ResumeOptimizationReviewDO review = iterationResult.review();
            CareerTaskStatus finalTaskStatus = toTaskStatus(review);
            CareerProgressEventDO finalEvent = persistProgressEvent(task.getId(), userId,
                    finalTaskStatus == CareerTaskStatus.SUCCESS ? "PASSED" : "NEEDS_REVIEW",
                    "Resume optimization review " + review.getStatus(),
                    buildProgressPayload(review, traceId, iterationResult.iterationNo()));
            progressEvents.add(finalEvent);
            task.setStatus(finalTaskStatus.name());
            task.setOutputJson(writeJson(buildTaskOutput(iterationResult.executorOutput(), review,
                    iterationResult.iterationNo(), finalTaskStatus),
                    "Failed to serialize optimization output JSON"));
            task.setErrorMessage(null);
            task.setUpdatedBy(userId);
            taskMapper.updateById(task);

            return toTaskVO(task, iterationResult.executorOutput(), iterationResult.suggestions(), review, progressEvents);
        } catch (RuntimeException ex) {
            try {
                markTaskFailed(task, userId, ex);
                persistProgressEvent(task.getId(), userId, "FAILED",
                        "Resume optimization failed: " + trimError(ex), Map.of("traceId", traceId));
            } catch (RuntimeException updateEx) {
                ex.addSuppressed(updateEx);
            }
            throw ex;
        }
    }

    @Override
    public CareerOptimizationTaskVO queryTask(String taskId) {
        String userId = requireUserId();
        ResumeOptimizationTaskDO task = requireTask(taskId, userId);
        ensureTaskLinksVisible(task, userId);
        return toTaskVO(task, readMap(task.getOutputJson(), "Failed to parse optimization output JSON"),
                listSuggestions(task.getId(), userId),
                latestReview(task.getId(), userId),
                listProgressEvents(task.getId(), userId));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerOptimizationSuggestionVO decideSuggestion(String suggestionId, CareerSuggestionDecisionRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Suggestion decision request is required");
        }
        ResumeOptimizationSuggestionDO suggestion = requireSuggestion(suggestionId, userId);
        ResumeOptimizationTaskDO task = requireTask(suggestion.getTaskId(), userId);
        ensureTaskLinksVisible(task, userId);
        ResumeSuggestionStatus status = parseDecisionStatus(request.getStatus());
        if (status == ResumeSuggestionStatus.EDITED) {
            String editedText = trimToNull(request.getEditedText());
            if (editedText == null) {
                throw new ClientException("Edited suggestion text is required");
            }
            suggestion.setSuggestedText(editedText);
        }
        suggestion.setStatus(status.name());
        suggestionMapper.updateById(suggestion);
        return toSuggestionVO(suggestion);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerResumeVersionVO generateVersionFromAcceptedSuggestions(String taskId) {
        String userId = requireUserId();
        ResumeOptimizationTaskDO task = requireTask(taskId, userId);
        if (!CareerTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            throw new ClientException("Optimization task is not successful");
        }
        ensureTaskLinksVisible(task, userId);
        ResumeOptimizationReviewDO review = latestReview(task.getId(), userId);
        if (review == null || !OptimizationReviewStatus.PASSED.name().equals(review.getStatus())) {
            throw new ClientException("Optimization review has not passed");
        }
        ResumeVersionDO original = requireResumeVersion(task.getResumeVersionId(), userId);
        List<ResumeOptimizationSuggestionDO> suggestions = listAcceptedOrEditedSuggestions(task.getId(), userId);
        if (suggestions.isEmpty()) {
            throw new ClientException("No accepted resume suggestions to generate version");
        }

        String optimizedMarkdown = applySuggestionReplacements(original.getMarkdownContent(), suggestions);
        String optimizedContentJson = buildOptimizedContentJson(original, optimizedMarkdown, task, suggestions);
        ResumeVersionDO version = ResumeVersionDO.builder()
                .userId(userId)
                .profileId(original.getProfileId())
                .documentId(original.getDocumentId())
                .title(buildOptimizedTitle(original.getTitle()))
                .versionNo(nextVersionNo(userId, original.getProfileId()))
                .sourceType(SOURCE_TYPE_OPTIMIZED)
                .contentJson(optimizedContentJson)
                .markdownContent(optimizedMarkdown)
                .active(1)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        resumeVersionMapper.insert(version);
        return toVersionVO(version);
    }

    private ResumeVersionDO requireResumeVersion(String resumeVersionId, String userId) {
        if (StrUtil.isBlank(resumeVersionId)) {
            throw new ClientException("Resume version id is required");
        }
        ResumeVersionDO resumeVersion = resumeVersionMapper.selectOne(
                Wrappers.lambdaQuery(ResumeVersionDO.class)
                        .eq(ResumeVersionDO::getId, resumeVersionId)
                        .eq(ResumeVersionDO::getUserId, userId)
                        .eq(ResumeVersionDO::getDeleted, 0)
        );
        if (resumeVersion == null) {
            throw new ClientException("Resume version does not exist");
        }
        return resumeVersion;
    }

    private JobDescriptionDO requireJob(String jdId, String userId) {
        if (StrUtil.isBlank(jdId)) {
            throw new ClientException("Job description id is required");
        }
        JobDescriptionDO job = jobDescriptionMapper.selectOne(
                Wrappers.lambdaQuery(JobDescriptionDO.class)
                        .eq(JobDescriptionDO::getId, jdId)
                        .eq(JobDescriptionDO::getUserId, userId)
                        .eq(JobDescriptionDO::getDeleted, 0)
        );
        if (job == null) {
            throw new ClientException("Job description does not exist");
        }
        return job;
    }

    private JobAlignmentReportDO requireAlignmentReport(String alignmentReportId, String userId) {
        JobAlignmentReportDO report = alignmentReportMapper.selectOne(
                Wrappers.lambdaQuery(JobAlignmentReportDO.class)
                        .eq(JobAlignmentReportDO::getId, alignmentReportId)
                        .eq(JobAlignmentReportDO::getUserId, userId)
                        .eq(JobAlignmentReportDO::getDeleted, 0)
        );
        if (report == null) {
            throw new ClientException("Alignment report does not exist");
        }
        return report;
    }

    private ResumeOptimizationTaskDO requireTask(String taskId, String userId) {
        if (StrUtil.isBlank(taskId)) {
            throw new ClientException("Optimization task id is required");
        }
        ResumeOptimizationTaskDO task = taskMapper.selectOne(
                Wrappers.lambdaQuery(ResumeOptimizationTaskDO.class)
                        .eq(ResumeOptimizationTaskDO::getId, taskId)
                        .eq(ResumeOptimizationTaskDO::getUserId, userId)
                        .eq(ResumeOptimizationTaskDO::getDeleted, 0)
        );
        if (task == null) {
            throw new ClientException("Optimization task does not exist");
        }
        return task;
    }

    private ResumeOptimizationSuggestionDO requireSuggestion(String suggestionId, String userId) {
        if (StrUtil.isBlank(suggestionId)) {
            throw new ClientException("Suggestion id is required");
        }
        ResumeOptimizationSuggestionDO suggestion = suggestionMapper.selectOne(
                Wrappers.lambdaQuery(ResumeOptimizationSuggestionDO.class)
                        .eq(ResumeOptimizationSuggestionDO::getId, suggestionId)
                        .eq(ResumeOptimizationSuggestionDO::getUserId, userId)
                        .eq(ResumeOptimizationSuggestionDO::getDeleted, 0)
        );
        if (suggestion == null) {
            throw new ClientException("Optimization suggestion does not exist");
        }
        return suggestion;
    }

    private void ensureAlignmentMatchesRequest(JobAlignmentReportDO report, String resumeVersionId, String jdId) {
        if (!Objects.equals(report.getResumeVersionId(), resumeVersionId)) {
            throw new ClientException("Alignment report does not match resume version");
        }
        if (StrUtil.isNotBlank(jdId) && !Objects.equals(report.getJdId(), jdId)) {
            throw new ClientException("Alignment report does not match job description");
        }
    }

    private void ensureTaskLinksVisible(ResumeOptimizationTaskDO task, String userId) {
        requireResumeVersion(task.getResumeVersionId(), userId);
        if (StrUtil.isBlank(task.getJdId())) {
            throw new ClientException("Optimization task does not exist");
        }
        requireJob(task.getJdId(), userId);
        String alignmentReportId = extractString(readMap(task.getInputJson(),
                "Failed to parse optimization input JSON").get("alignmentReportId"));
        if (StrUtil.isNotBlank(alignmentReportId)) {
            JobAlignmentReportDO alignmentReport = requireAlignmentReport(alignmentReportId, userId);
            ensureAlignmentMatchesRequest(alignmentReport, task.getResumeVersionId(), task.getJdId());
        }
    }

    private String buildInputJson(ResumeVersionDO resumeVersion,
                                  JobDescriptionDO job,
                                  JobAlignmentReportDO alignmentReport) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("resumeVersionId", resumeVersion.getId());
        input.put("jdId", job == null ? null : job.getId());
        input.put("alignmentReportId", alignmentReport == null ? null : alignmentReport.getId());
        input.put("resume", readJsonValueOrDefault(resumeVersion.getContentJson(), Map.of(),
                "Failed to parse resume content JSON"));
        input.put("jd", job == null ? Map.of() : readJsonValueOrDefault(job.getParsedJson(), Map.of(),
                "Failed to parse job description JSON"));
        input.put("alignmentReport", alignmentReport == null ? Map.of() : buildAlignmentReportPayload(alignmentReport));
        return writeJson(input, "Failed to serialize optimization input JSON");
    }

    private String buildPrompt(ResumeVersionDO resumeVersion,
                               JobDescriptionDO job,
                               JobAlignmentReportDO alignmentReport,
                               CareerRetrievalEnhancement retrievalEnhancement) {
        return buildPrompt(resumeVersion, job, alignmentReport, retrievalEnhancement, null);
    }

    /**
     * 构建执行者提示词，并追加上一轮裁判修改意见驱动下一轮优化。
     */
    private String buildPrompt(ResumeVersionDO resumeVersion,
                               JobDescriptionDO job,
                               JobAlignmentReportDO alignmentReport,
                               CareerRetrievalEnhancement retrievalEnhancement,
                               Map<String, Object> previousReviewerOutput) {
        String alignmentJson = alignmentReport == null
                ? "{}"
                : writeJson(buildAlignmentReportPayload(alignmentReport), "Failed to serialize alignment report JSON");
        String prompt = String.format(
                CareerPromptTemplates.RESUME_OPTIMIZE,
                defaultJson(resumeVersion.getContentJson()),
                job == null ? "{}" : defaultJson(job.getParsedJson()),
                alignmentJson
        );
        if (previousReviewerOutput != null && !previousReviewerOutput.isEmpty()) {
            prompt = prompt + "\n\n上一轮裁判修改意见 JSON：\n"
                    + writeJson(buildRevisionPayload(previousReviewerOutput),
                    "Failed to serialize optimization revision instructions JSON")
                    + "\n请只修正被裁判指出的问题，不要编造原始简历中不存在的经历。";
        }
        return appendRetrievalEvidence(prompt, retrievalEnhancement);
    }

    /**
     * 调用执行者 Agent 生成本轮简历优化建议。
     */
    private Map<String, Object> callExecutor(ResumeOptimizationTaskDO task,
                                             ResumeVersionDO resumeVersion,
                                             JobDescriptionDO job,
                                             JobAlignmentReportDO alignmentReport,
                                             CareerRetrievalEnhancement retrievalEnhancement,
                                             Map<String, Object> previousReviewerOutput,
                                             int iterationNo) {
        String prompt = buildPrompt(resumeVersion, job, alignmentReport, retrievalEnhancement, previousReviewerOutput);
        String response = singleFlightLlmService.chat("OPTIMIZATION_EXECUTOR",
                buildSingleFlightKey("OPTIMIZATION_EXECUTOR", task.getUserId(), task.getId(),
                        iterationNo + ":" + prompt),
                task.getTraceId(),
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(prompt)))
                        .temperature(0.1D)
                        .thinking(false)
                        .build());
        Map<String, Object> output = careerJsonParser.parseObject(response);
        return output == null ? Map.of() : output;
    }

    /**
     * 运行 JobNavigator 的多轮执行者-裁判优化闭环。
     */
    private OptimizationIterationResult runOptimizationIterations(ResumeOptimizationTaskDO task,
                                                                 String userId,
                                                                 ResumeVersionDO resumeVersion,
                                                                 JobDescriptionDO job,
                                                                 JobAlignmentReportDO alignmentReport,
                                                                 CareerRetrievalEnhancement retrievalEnhancement,
                                                                 List<CareerProgressEventDO> progressEvents) {
        Map<String, Object> previousReviewerOutput = null;
        Map<String, Object> finalOutput = Map.of();
        ResumeOptimizationReviewDO finalReview = null;
        int finalIterationNo = 1;
        for (int iterationNo = 1; iterationNo <= MAX_OPTIMIZATION_ITERATIONS; iterationNo++) {
            progressEvents.add(persistProgressEvent(task.getId(), userId, "GENERATING",
                    "Generating resume optimization suggestions, iteration " + iterationNo,
                    Map.of("traceId", task.getTraceId(), "iterationNo", iterationNo)));
            Map<String, Object> output = callExecutor(task, resumeVersion, job, alignmentReport,
                    retrievalEnhancement, previousReviewerOutput, iterationNo);
            progressEvents.add(persistProgressEvent(task.getId(), userId, "REVIEWING",
                    "Reviewing optimization quality and truthfulness, iteration " + iterationNo,
                    Map.of("traceId", task.getTraceId(), "iterationNo", iterationNo)));
            Map<String, Object> reviewerOutput =
                    callReviewer(task, resumeVersion, job, alignmentReport, retrievalEnhancement, output);
            List<ResumeOptimizationSuggestionDO> reviewSuggestions = buildSuggestionDrafts(task.getId(), userId,
                    output.get("suggestions"));
            ResumeOptimizationReviewDO review = persistReview(task, userId, iterationNo, output,
                    reviewerOutput, reviewSuggestions);

            finalOutput = output;
            finalReview = review;
            finalIterationNo = iterationNo;
            OptimizationReviewStatus status = OptimizationReviewStatus.valueOf(review.getStatus());
            if (status == OptimizationReviewStatus.PASSED || status == OptimizationReviewStatus.BLOCKED_BY_RISK
                    || iterationNo == MAX_OPTIMIZATION_ITERATIONS) {
                break;
            }
            previousReviewerOutput = reviewerOutput;
            progressEvents.add(persistProgressEvent(task.getId(), userId, "REVISING",
                    "Reviewer requested another executor iteration",
                    buildProgressPayload(review, task.getTraceId(), iterationNo)));
        }
        List<ResumeOptimizationSuggestionDO> suggestions = persistSuggestions(task.getId(), userId,
                finalOutput.get("suggestions"));
        return new OptimizationIterationResult(finalOutput, suggestions, finalReview, finalIterationNo);
    }

    /**
     * 提取裁判输出中用于驱动下一轮执行者修订的信息。
     */
    private Map<String, Object> buildRevisionPayload(Map<String, Object> reviewerOutput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualityScore", reviewerOutput.get("qualityScore"));
        payload.put("acceptedSuggestionIds", reviewerOutput.get("acceptedSuggestionIds"));
        payload.put("rejectedSuggestionIds", reviewerOutput.get("rejectedSuggestionIds"));
        payload.put("revisionInstructions", reviewerOutput.get("revisionInstructions"));
        payload.put("riskSummary", reviewerOutput.get("riskSummary"));
        return payload;
    }

    private Map<String, Object> callReviewer(ResumeOptimizationTaskDO task,
                                             ResumeVersionDO resumeVersion,
                                             JobDescriptionDO job,
                                             JobAlignmentReportDO alignmentReport,
                                             CareerRetrievalEnhancement retrievalEnhancement,
                                             Map<String, Object> executorOutput) {
        String alignmentJson = alignmentReport == null
                ? "{}"
                : writeJson(buildAlignmentReportPayload(alignmentReport), "Failed to serialize alignment report JSON");
        String reviewerPrompt = appendRetrievalEvidence(String.format(
                CareerPromptTemplates.RESUME_OPTIMIZATION_REVIEW,
                defaultJson(resumeVersion.getContentJson()),
                job == null ? "{}" : defaultJson(job.getParsedJson()),
                alignmentJson,
                writeJson(executorOutput, "Failed to serialize optimization executor JSON")
        ), retrievalEnhancement);
        String reviewerResponse = singleFlightLlmService.chat("OPTIMIZATION_REVIEW",
                buildSingleFlightKey("OPTIMIZATION_REVIEW", task.getUserId(), task.getId(), reviewerPrompt),
                task.getTraceId(),
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(reviewerPrompt)))
                        .temperature(0.1D)
                        .thinking(false)
                        .build());
        Map<String, Object> reviewerOutput = careerJsonParser.parseObject(reviewerResponse);
        return reviewerOutput == null ? Map.of() : reviewerOutput;
    }

    private Map<String, Object> buildAlignmentReportPayload(JobAlignmentReportDO report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", report.getId());
        payload.put("resumeVersionId", report.getResumeVersionId());
        payload.put("jdId", report.getJdId());
        payload.put("score", report.getScore());
        payload.put("summary", report.getSummary());
        payload.put("evidence", readJsonValueOrDefault(report.getEvidenceJson(), List.of(),
                "Failed to parse alignment evidence JSON"));
        payload.put("gaps", readJsonValueOrDefault(report.getGapsJson(), List.of(),
                "Failed to parse alignment gaps JSON"));
        payload.put("risks", readJsonValueOrDefault(report.getRisksJson(), List.of(),
                "Failed to parse alignment risks JSON"));
        payload.put("traceId", report.getTraceId());
        return payload;
    }

    /**
     * 将最终通过或最终停留轮次的建议落库为用户可决策项。
     */
    private List<ResumeOptimizationSuggestionDO> persistSuggestions(String taskId, String userId, Object suggestionsValue) {
        List<ResumeOptimizationSuggestionDO> persisted = new ArrayList<>();
        try {
            for (ResumeOptimizationSuggestionDO suggestion : buildSuggestionDrafts(taskId, userId, suggestionsValue)) {
                suggestionMapper.insert(suggestion);
                persisted.add(suggestion);
            }
        } catch (RuntimeException ex) {
            deletePersistedSuggestions(persisted, ex);
            throw ex;
        }
        return persisted;
    }

    /**
     * 将执行者输出转换为建议草稿，供裁判评分和最终落库共用。
     */
    private List<ResumeOptimizationSuggestionDO> buildSuggestionDrafts(String taskId,
                                                                       String userId,
                                                                       Object suggestionsValue) {
        List<ResumeOptimizationSuggestionDO> drafts = new ArrayList<>();
        for (Object item : toObjectList(suggestionsValue)) {
            if (!(item instanceof Map<?, ?> suggestionMap)) {
                continue;
            }
            drafts.add(ResumeOptimizationSuggestionDO.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .category(limitText(extractString(suggestionMap.get("category")), CATEGORY_MAX_LENGTH))
                    .title(limitText(extractString(suggestionMap.get("title")), TITLE_MAX_LENGTH))
                    .originalText(extractString(suggestionMap.get("originalText")))
                    .suggestedText(extractString(suggestionMap.get("suggestedText")))
                    .reason(extractString(suggestionMap.get("reason")))
                    .riskLevel(limitText(extractString(suggestionMap.get("riskLevel")), RISK_LEVEL_MAX_LENGTH))
                    .status(ResumeSuggestionStatus.PENDING.name())
                    .build());
        }
        return drafts;
    }

    private void deletePersistedSuggestions(List<ResumeOptimizationSuggestionDO> suggestions, RuntimeException cause) {
        for (ResumeOptimizationSuggestionDO suggestion : suggestions) {
            if (StrUtil.isBlank(suggestion.getId())) {
                continue;
            }
            try {
                suggestionMapper.deleteById(suggestion.getId());
            } catch (RuntimeException deleteEx) {
                cause.addSuppressed(deleteEx);
            }
        }
    }

    /**
     * 持久化单轮执行者输出和裁判评审结果。
     */
    private ResumeOptimizationReviewDO persistReview(ResumeOptimizationTaskDO task,
                                                     String userId,
                                                     int iterationNo,
                                                     Map<String, Object> output,
                                                     Map<String, Object> reviewerOutput,
                                                     List<ResumeOptimizationSuggestionDO> suggestions) {
        ResumeOptimizationReviewEvaluator.Decision decision = reviewEvaluator.evaluate(reviewerOutput, suggestions);
        ResumeOptimizationReviewDO review = ResumeOptimizationReviewDO.builder()
                .taskId(task.getId())
                .userId(userId)
                .iterationNo(iterationNo)
                .executorOutputJson(writeJson(output, "Failed to serialize optimization review executor JSON"))
                .reviewerOutputJson(writeJson(reviewerOutput, "Failed to serialize optimization reviewer JSON"))
                .qualityScore(BigDecimal.valueOf(decision.qualityScore()))
                .truthfulnessRisk(decision.truthfulnessRisk())
                .status(decision.status().name())
                .traceId(task.getTraceId())
                .errorMessage(null)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        reviewMapper.insert(review);
        return review;
    }

    private CareerTaskStatus toTaskStatus(ResumeOptimizationReviewDO review) {
        return review != null && OptimizationReviewStatus.PASSED.name().equals(review.getStatus())
                ? CareerTaskStatus.SUCCESS
                : CareerTaskStatus.NEEDS_REVIEW;
    }

    /**
     * 构建任务输出，记录最终轮执行者结果和多轮评审摘要。
     */
    private Map<String, Object> buildTaskOutput(Map<String, Object> output,
                                                ResumeOptimizationReviewDO review,
                                                int iterationNo,
                                                CareerTaskStatus taskStatus) {
        Map<String, Object> payload = new LinkedHashMap<>(output == null ? Map.of() : output);
        payload.put("finalIterationNo", iterationNo);
        payload.put("reviewStatus", review == null ? null : review.getStatus());
        payload.put("taskStatus", taskStatus.name());
        payload.put("maxIterationNo", MAX_OPTIMIZATION_ITERATIONS);
        return payload;
    }

    private Map<String, Object> buildProgressPayload(ResumeOptimizationReviewDO review, String traceId) {
        return buildProgressPayload(review, traceId, review == null ? null : review.getIterationNo());
    }

    /**
     * 构建进度事件负载，带上轮次和裁判评分信息。
     */
    private Map<String, Object> buildProgressPayload(ResumeOptimizationReviewDO review,
                                                     String traceId,
                                                     Integer iterationNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", traceId);
        payload.put("iterationNo", iterationNo);
        payload.put("reviewId", review == null ? null : review.getId());
        payload.put("reviewStatus", review == null ? null : review.getStatus());
        payload.put("qualityScore", review == null ? null : review.getQualityScore());
        payload.put("truthfulnessRisk", review == null ? null : review.getTruthfulnessRisk());
        return payload;
    }

    private CareerProgressEventDO persistProgressEvent(String taskId,
                                                       String userId,
                                                       String eventType,
                                                       String message,
                                                       Map<String, Object> payload) {
        CareerProgressEventDO event = CareerProgressEventDO.builder()
                .taskId(taskId)
                .userId(userId)
                .eventType(eventType)
                .message(limitText(message, EVENT_MESSAGE_MAX_LENGTH))
                .payloadJson(writeJson(payload == null ? Map.of() : payload,
                        "Failed to serialize progress event payload JSON"))
                .build();
        progressEventMapper.insert(event);
        return event;
    }

    private void markTaskFailed(ResumeOptimizationTaskDO task, String userId, RuntimeException ex) {
        task.setStatus(CareerTaskStatus.FAILED.name());
        task.setErrorMessage(trimError(ex));
        task.setUpdatedBy(userId);
        taskMapper.updateById(task);
    }

    private List<ResumeOptimizationSuggestionDO> listSuggestions(String taskId, String userId) {
        List<ResumeOptimizationSuggestionDO> suggestions = suggestionMapper.selectList(
                Wrappers.lambdaQuery(ResumeOptimizationSuggestionDO.class)
                        .eq(ResumeOptimizationSuggestionDO::getTaskId, taskId)
                        .eq(ResumeOptimizationSuggestionDO::getUserId, userId)
                        .eq(ResumeOptimizationSuggestionDO::getDeleted, 0)
                        .orderByAsc(ResumeOptimizationSuggestionDO::getCreateTime)
                        .orderByAsc(ResumeOptimizationSuggestionDO::getId)
        );
        return suggestions == null ? List.of() : suggestions;
    }

    private ResumeOptimizationReviewDO latestReview(String taskId, String userId) {
        List<ResumeOptimizationReviewDO> reviews = reviewMapper.selectList(
                Wrappers.lambdaQuery(ResumeOptimizationReviewDO.class)
                        .eq(ResumeOptimizationReviewDO::getTaskId, taskId)
                        .eq(ResumeOptimizationReviewDO::getUserId, userId)
                        .eq(ResumeOptimizationReviewDO::getDeleted, 0)
                        .orderByDesc(ResumeOptimizationReviewDO::getIterationNo)
                        .orderByDesc(ResumeOptimizationReviewDO::getCreateTime)
                        .last("LIMIT 1")
        );
        return reviews == null || reviews.isEmpty() ? null : reviews.get(0);
    }

    private List<CareerProgressEventDO> listProgressEvents(String taskId, String userId) {
        List<CareerProgressEventDO> events = progressEventMapper.selectList(
                Wrappers.lambdaQuery(CareerProgressEventDO.class)
                        .eq(CareerProgressEventDO::getTaskId, taskId)
                        .eq(CareerProgressEventDO::getUserId, userId)
                        .eq(CareerProgressEventDO::getDeleted, 0)
                        .orderByAsc(CareerProgressEventDO::getCreateTime)
                        .orderByAsc(CareerProgressEventDO::getId)
        );
        return events == null ? List.of() : events;
    }

    private List<ResumeOptimizationSuggestionDO> listAcceptedOrEditedSuggestions(String taskId, String userId) {
        List<ResumeOptimizationSuggestionDO> suggestions = suggestionMapper.selectList(
                Wrappers.lambdaQuery(ResumeOptimizationSuggestionDO.class)
                        .eq(ResumeOptimizationSuggestionDO::getTaskId, taskId)
                        .eq(ResumeOptimizationSuggestionDO::getUserId, userId)
                        .eq(ResumeOptimizationSuggestionDO::getDeleted, 0)
                        .in(ResumeOptimizationSuggestionDO::getStatus,
                                ResumeSuggestionStatus.ACCEPTED.name(),
                                ResumeSuggestionStatus.EDITED.name())
                        .orderByAsc(ResumeOptimizationSuggestionDO::getCreateTime)
                        .orderByAsc(ResumeOptimizationSuggestionDO::getId)
        );
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.stream()
                .filter(suggestion -> ResumeSuggestionStatus.ACCEPTED.name().equals(suggestion.getStatus())
                        || ResumeSuggestionStatus.EDITED.name().equals(suggestion.getStatus()))
                .toList();
    }

    private Integer nextVersionNo(String userId, String profileId) {
        LambdaQueryWrapper<ResumeVersionDO> wrapper = Wrappers.lambdaQuery(ResumeVersionDO.class)
                .eq(ResumeVersionDO::getUserId, userId)
                .eq(ResumeVersionDO::getDeleted, 0);
        if (StrUtil.isBlank(profileId)) {
            wrapper.isNull(ResumeVersionDO::getProfileId);
        } else {
            wrapper.eq(ResumeVersionDO::getProfileId, profileId);
        }
        List<ResumeVersionDO> versions = resumeVersionMapper.selectList(wrapper);
        if (versions == null) {
            return 1;
        }
        return versions.stream()
                .map(ResumeVersionDO::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String applySuggestionReplacements(String markdownContent, List<ResumeOptimizationSuggestionDO> suggestions) {
        if (markdownContent == null || suggestions.isEmpty()) {
            return markdownContent;
        }
        String result = markdownContent;
        for (ResumeOptimizationSuggestionDO suggestion : suggestions) {
            String originalText = suggestion.getOriginalText();
            String suggestedText = suggestion.getSuggestedText();
            if (StrUtil.isNotBlank(originalText) && suggestedText != null && result.contains(originalText)) {
                result = result.replace(originalText, suggestedText);
            }
        }
        return result;
    }

    private String buildOptimizedContentJson(ResumeVersionDO original,
                                             String optimizedMarkdown,
                                             ResumeOptimizationTaskDO task,
                                             List<ResumeOptimizationSuggestionDO> suggestions) {
        Map<String, Object> content = new LinkedHashMap<>();
        Object originalContent = readJsonValueOrDefault(original.getContentJson(), Map.of(),
                "Failed to parse original resume content JSON");
        content.put("profile", originalContent);
        content.put("markdownContent", StrUtil.blankToDefault(optimizedMarkdown, ""));
        content.put("sourceResumeVersionId", original.getId());
        content.put("optimizationTaskId", task.getId());
        content.put("appliedSuggestions", suggestions.stream()
                .map(this::toAppliedSuggestionPayload)
                .toList());
        return writeJson(content, "Failed to serialize optimized resume content JSON");
    }

    private Map<String, Object> toAppliedSuggestionPayload(ResumeOptimizationSuggestionDO suggestion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", suggestion.getId());
        payload.put("category", suggestion.getCategory());
        payload.put("title", suggestion.getTitle());
        payload.put("originalText", suggestion.getOriginalText());
        payload.put("suggestedText", suggestion.getSuggestedText());
        payload.put("status", suggestion.getStatus());
        payload.put("riskLevel", suggestion.getRiskLevel());
        return payload;
    }

    private CareerOptimizationTaskVO toTaskVO(ResumeOptimizationTaskDO task,
                                              Map<String, Object> output,
                                              List<ResumeOptimizationSuggestionDO> suggestions,
                                              ResumeOptimizationReviewDO review,
                                              List<CareerProgressEventDO> progressEvents) {
        return CareerOptimizationTaskVO.builder()
                .id(task.getId())
                .status(task.getStatus())
                .resumeVersionId(task.getResumeVersionId())
                .jdId(task.getJdId())
                .summary(extractString(output.get("summary")))
                .qualityScore(review == null ? null : review.getQualityScore())
                .reviewStatus(review == null ? null : review.getStatus())
                .riskSummary(extractRiskSummary(review))
                .suggestions(suggestions.stream().map(this::toSuggestionVO).toList())
                .progressEvents(progressEvents.stream().map(this::toProgressEventVO).toList())
                .traceId(task.getTraceId())
                .build();
    }

    private String extractRiskSummary(ResumeOptimizationReviewDO review) {
        if (review == null) {
            return null;
        }
        return extractString(readMap(review.getReviewerOutputJson(),
                "Failed to parse optimization reviewer JSON").get("riskSummary"));
    }

    private CareerProgressEventVO toProgressEventVO(CareerProgressEventDO event) {
        return CareerProgressEventVO.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .message(event.getMessage())
                .payloadJson(event.getPayloadJson())
                .createTime(event.getCreateTime())
                .build();
    }

    private CareerOptimizationSuggestionVO toSuggestionVO(ResumeOptimizationSuggestionDO suggestion) {
        return CareerOptimizationSuggestionVO.builder()
                .id(suggestion.getId())
                .category(suggestion.getCategory())
                .title(suggestion.getTitle())
                .originalText(suggestion.getOriginalText())
                .suggestedText(suggestion.getSuggestedText())
                .reason(suggestion.getReason())
                .riskLevel(suggestion.getRiskLevel())
                .status(suggestion.getStatus())
                .build();
    }

    private CareerResumeVersionVO toVersionVO(ResumeVersionDO version) {
        return CareerResumeVersionVO.builder()
                .id(version.getId())
                .profileId(version.getProfileId())
                .versionNo(version.getVersionNo())
                .title(version.getTitle())
                .content(version.getContentJson())
                .markdownContent(version.getMarkdownContent())
                .createTime(version.getCreateTime())
                .build();
    }

    private ResumeSuggestionStatus parseDecisionStatus(String value) {
        String status = trimToNull(value);
        if (status == null) {
            throw new ClientException("Suggestion status is required");
        }
        try {
            ResumeSuggestionStatus suggestionStatus = ResumeSuggestionStatus.valueOf(status.toUpperCase(Locale.ROOT));
            if (suggestionStatus == ResumeSuggestionStatus.PENDING) {
                throw new ClientException("Suggestion status is invalid");
            }
            return suggestionStatus;
        } catch (IllegalArgumentException ex) {
            throw new ClientException("Suggestion status is invalid");
        }
    }

    private Map<String, Object> readMap(String value, String errorMessage) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private Object readJsonValueOrDefault(String value, Object defaultValue, String errorMessage) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private List<Object> toObjectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String defaultJson(String value) {
        String text = trimToNull(value);
        return text == null ? "{}" : text;
    }

    private String buildSingleFlightKey(String scene, String userId, String artifactId, String prompt) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "CAREER_AI"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"),
                StrUtil.blankToDefault(prompt, ""));
    }

    private String appendRetrievalEvidence(String prompt, CareerRetrievalEnhancement enhancement) {
        if (enhancement == null) {
            return prompt;
        }
        return prompt + "\n\nCareer retrieval evidence JSON:\n"
                + writeJson(enhancement.toPromptPayload(), "Failed to serialize retrieval evidence JSON")
                + "\nRules: HYDE_QUERY evidence is QUERY_ONLY and must never be copied into resume content, suggestedText, or markdown.";
    }

    private String buildOptimizedTitle(String title) {
        String base = trimToNull(title);
        if (base == null) {
            return DEFAULT_OPTIMIZED_TITLE;
        }
        return limitText(base + " Optimized", TITLE_MAX_LENGTH);
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String limitText(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String trimError(RuntimeException ex) {
        String message = ex.getMessage();
        if (StrUtil.isBlank(message)) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() <= TASK_ERROR_MAX_LENGTH ? message : message.substring(0, TASK_ERROR_MAX_LENGTH);
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }

    private record OptimizationIterationResult(Map<String, Object> executorOutput,
                                                List<ResumeOptimizationSuggestionDO> suggestions,
                                                ResumeOptimizationReviewDO review,
                                                int iterationNo) {
    }
}
