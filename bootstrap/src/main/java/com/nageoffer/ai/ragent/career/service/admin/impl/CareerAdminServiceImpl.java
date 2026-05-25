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

package com.nageoffer.ai.ragent.career.service.admin.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminAgentTraceVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminOverviewVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminRubricDimensionVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminRubricVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminTaskItemVO;
import com.nageoffer.ai.ragent.career.dao.entity.CandidateProfileDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentExecutionTraceDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerTaskAttemptDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeDocumentDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeExportRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationReviewDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationTaskDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CandidateProfileMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerSingleFlightRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerTaskAttemptMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeDocumentMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeExportRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationReviewMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationTaskMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.enums.OptimizationReviewStatus;
import com.nageoffer.ai.ragent.career.service.admin.CareerAdminService;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CareerAdminServiceImpl implements CareerAdminService {

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResumeDocumentMapper resumeDocumentMapper;

    private final CandidateProfileMapper candidateProfileMapper;

    private final ResumeVersionMapper resumeVersionMapper;

    private final JobDescriptionMapper jobDescriptionMapper;

    private final JobAlignmentReportMapper jobAlignmentReportMapper;

    private final ResumeOptimizationTaskMapper resumeOptimizationTaskMapper;

    private final ResumeOptimizationReviewMapper resumeOptimizationReviewMapper;

    private final InterviewSessionMapper interviewSessionMapper;

    private final InterviewReportMapper interviewReportMapper;

    private final CareerSingleFlightRecordMapper careerSingleFlightRecordMapper;

    private final CareerTaskAttemptMapper careerTaskAttemptMapper;

    private final ResumeExportRecordMapper resumeExportRecordMapper;

    private final CareerAgentTraceService careerAgentTraceService;

    @Override
    public CareerAdminOverviewVO overview() {
        return CareerAdminOverviewVO.builder()
                .resumeDocuments(countAll(resumeDocumentMapper::selectCount))
                .candidateProfiles(countAll(candidateProfileMapper::selectCount))
                .resumeVersions(countAll(resumeVersionMapper::selectCount))
                .jobDescriptions(countAll(jobDescriptionMapper::selectCount))
                .alignmentReports(countAll(jobAlignmentReportMapper::selectCount))
                .optimizationTasks(countAll(resumeOptimizationTaskMapper::selectCount))
                .optimizationReviewPassed(countReviews(OptimizationReviewStatus.PASSED.name()))
                .optimizationReviewNeedsRevision(countReviews(OptimizationReviewStatus.NEEDS_REVISION.name()))
                .optimizationReviewBlockedByRisk(countReviews(OptimizationReviewStatus.BLOCKED_BY_RISK.name()))
                .interviewSessions(countAll(interviewSessionMapper::selectCount))
                .interviewReports(countAll(interviewReportMapper::selectCount))
                .singleFlightRecords(countAll(careerSingleFlightRecordMapper::selectCount))
                .singleFlightRunning(countSingleFlight("RUNNING"))
                .singleFlightSuccess(countSingleFlight("SUCCESS"))
                .singleFlightFailed(countSingleFlight("FAILED"))
                .taskAttempts(countAll(careerTaskAttemptMapper::selectCount))
                .taskAttemptFailed(countAttempts("FAILED"))
                .taskAttemptReplayed(countAttempts("REPLAYED"))
                .failedExports(countExports("FAILED"))
                .highlights(List.of(
                        "Judge-executor resume optimization with quality gate",
                        "Interview turn idempotency and recovery snapshots",
                        "Career AI Single-flight with fencing token and replay",
                        "HyDE/Rerank evidence and render pipeline gate"
                ))
                .build();
    }

    @Override
    public List<CareerAdminTaskItemVO> tasks(Integer limit, String type, String status) {
        int resolvedLimit = normalizeLimit(limit);
        List<CareerAdminTaskItemVO> items = new ArrayList<>();
        Map<String, ResumeOptimizationReviewDO> reviews = latestReviewsByTaskId();

        selectLatestResumeDocuments(resolvedLimit).stream()
                .map(this::toResumeDocumentTask)
                .forEach(items::add);
        selectLatestAlignmentReports(resolvedLimit).stream()
                .map(this::toAlignmentTask)
                .forEach(items::add);
        selectLatestOptimizationTasks(resolvedLimit).stream()
                .map(task -> toOptimizationTask(task, reviews.get(task.getId())))
                .forEach(items::add);
        selectLatestInterviewSessions(resolvedLimit).stream()
                .map(this::toInterviewSessionTask)
                .forEach(items::add);
        selectLatestInterviewReports(resolvedLimit).stream()
                .map(this::toInterviewReportTask)
                .forEach(items::add);
        selectLatestExports(resolvedLimit).stream()
                .map(this::toExportTask)
                .forEach(items::add);
        selectLatestSingleFlightRecords(resolvedLimit).stream()
                .map(this::toSingleFlightTask)
                .forEach(items::add);
        selectLatestTaskAttempts(resolvedLimit).stream()
                .map(this::toAttemptTask)
                .forEach(items::add);

        String normalizedType = normalize(type);
        String normalizedStatus = normalize(status);
        return items.stream()
                .filter(item -> normalizedType == null || normalizedType.equals(item.getType()))
                .filter(item -> normalizedStatus == null || normalizedStatus.equals(item.getStatus()))
                .sorted(Comparator.comparing(CareerAdminTaskItemVO::getUpdateTime,
                        Comparator.nullsLast(Date::compareTo).reversed()))
                .limit(resolvedLimit)
                .toList();
    }

    @Override
    public List<CareerAdminRubricVO> rubrics() {
        return List.of(
                CareerAdminRubricVO.builder()
                        .id("career-java-backend-v1")
                        .name("Java Backend / AI Application Rubric")
                        .version("v1")
                        .editable(false)
                        .dimensions(List.of(
                                dimension("technicalDepth", "Technical Depth", 25,
                                        List.of("Spring Boot", "RAG", "Agent", "PostgreSQL", "Redis")),
                                dimension("projectOwnership", "Project Ownership", 20,
                                        List.of("architecture", "state machine", "idempotency", "observability")),
                                dimension("engineeringQuality", "Engineering Quality", 20,
                                        List.of("tests", "trace", "fallback", "data consistency")),
                                dimension("communication", "Structured Communication", 15,
                                        List.of("STAR", "tradeoff", "evidence")),
                                dimension("jobFit", "JD Fit", 20,
                                        List.of("hard requirements", "bonus points", "risk gaps"))
                        ))
                        .build(),
                CareerAdminRubricVO.builder()
                        .id("career-general-v1")
                        .name("General Career Rubric")
                        .version("v1")
                        .editable(false)
                        .dimensions(List.of(
                                dimension("roleFit", "Role Fit", 25,
                                        List.of("responsibility match", "domain relevance")),
                                dimension("evidence", "Evidence Quality", 25,
                                        List.of("specific examples", "measurable impact")),
                                dimension("truthfulness", "Truthfulness", 20,
                                        List.of("unsupported claim detection", "risk flags")),
                                dimension("communication", "Communication", 15,
                                        List.of("clarity", "structure")),
                                dimension("growthPotential", "Growth Potential", 15,
                                        List.of("learning velocity", "reflection"))
                        ))
                        .build()
        );
    }

    /**
     * 查询最近的 Agent 调用观测记录，并转换为管理端稳定视图。
     */
    @Override
    public List<CareerAdminAgentTraceVO> agentTraces(Integer limit, String agentType, String status) {
        return careerAgentTraceService.listRecentExecutions(limit, agentType, status).stream()
                .map(this::toAgentTrace)
                .toList();
    }

    /**
     * 将 Agent 调用记录转换为管理端视图。
     */
    private CareerAdminAgentTraceVO toAgentTrace(CareerAgentExecutionTraceDO trace) {
        return CareerAdminAgentTraceVO.builder()
                .id(trace.getId())
                .agentType(trace.getAgentType())
                .scene(trace.getScene())
                .sessionId(trace.getSessionId())
                .userId(trace.getUserId())
                .traceId(trace.getTraceId())
                .modelName(trace.getModelName())
                .status(trace.getStatus())
                .inputSummary(trace.getInputSummary())
                .outputSummary(trace.getOutputSummary())
                .latencyMs(trace.getLatencyMs())
                .errorType(trace.getErrorType())
                .errorMessage(trace.getErrorMessage())
                .createTime(trace.getCreateTime())
                .updateTime(trace.getUpdateTime())
                .build();
    }

    private CareerAdminTaskItemVO toResumeDocumentTask(ResumeDocumentDO document) {
        return CareerAdminTaskItemVO.builder()
                .id(document.getId())
                .type("RESUME_DOCUMENT")
                .status(document.getParseStatus())
                .userId(document.getUserId())
                .businessId(document.getProfileId())
                .summary(document.getOriginalName())
                .failureReason(document.getParseError())
                .traceId(document.getTraceId())
                .createTime(document.getCreateTime())
                .updateTime(document.getUpdateTime())
                .build();
    }

    private CareerAdminTaskItemVO toAlignmentTask(JobAlignmentReportDO report) {
        return CareerAdminTaskItemVO.builder()
                .id(report.getId())
                .type("ALIGNMENT_REPORT")
                .status("SUCCESS")
                .userId(report.getUserId())
                .businessId(report.getResumeVersionId())
                .summary(report.getSummary())
                .traceId(report.getTraceId())
                .createTime(report.getCreateTime())
                .updateTime(report.getUpdateTime())
                .build();
    }

    private CareerAdminTaskItemVO toOptimizationTask(ResumeOptimizationTaskDO task, ResumeOptimizationReviewDO review) {
        return CareerAdminTaskItemVO.builder()
                .id(task.getId())
                .type("OPTIMIZATION_TASK")
                .status(task.getStatus())
                .userId(task.getUserId())
                .businessId(task.getResumeVersionId())
                .summary(firstNonBlank(readString(task.getOutputJson(), "summary"), task.getErrorMessage()))
                .failureReason(task.getErrorMessage())
                .traceId(firstNonBlank(review == null ? null : review.getTraceId(), task.getTraceId()))
                .createTime(task.getCreateTime())
                .updateTime(latest(task.getUpdateTime(), review == null ? null : review.getUpdateTime()))
                .qualityScore(review == null ? null : review.getQualityScore())
                .reviewStatus(review == null ? null : review.getStatus())
                .riskFlag(review == null ? null : review.getTruthfulnessRisk())
                .riskSummary(review == null ? null : readString(review.getReviewerOutputJson(), "riskSummary"))
                .build();
    }

    private CareerAdminTaskItemVO toInterviewSessionTask(InterviewSessionDO session) {
        return CareerAdminTaskItemVO.builder()
                .id(session.getId())
                .type("INTERVIEW_SESSION")
                .status(session.getStatus())
                .runtimeStatus(session.getStatus())
                .userId(session.getUserId())
                .businessId(session.getResumeVersionId())
                .summary("currentTurnNo=" + valueOrDash(session.getCurrentTurnNo()))
                .traceId(session.getTraceId())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .currentTurnNo(session.getCurrentTurnNo())
                .build();
    }

    private CareerAdminTaskItemVO toAttemptTask(CareerTaskAttemptDO attempt) {
        return CareerAdminTaskItemVO.builder()
                .id(attempt.getId())
                .type("TASK_ATTEMPT")
                .status(attempt.getStatus())
                .userId(attempt.getUserId())
                .businessId(attempt.getBusinessId())
                .summary(firstNonBlank(attempt.getPromptSummary(), attempt.getErrorMessage()))
                .failureReason(attempt.getErrorMessage())
                .traceId(attempt.getTraceId())
                .createTime(attempt.getCreateTime())
                .updateTime(attempt.getUpdateTime())
                .scene(attempt.getScene())
                .modelName(attempt.getModelName())
                .replayed(attempt.getReplayed())
                .latencyMs(attempt.getLatencyMs())
                .build();
    }

    private CareerAdminTaskItemVO toInterviewReportTask(InterviewReportDO report) {
        return CareerAdminTaskItemVO.builder()
                .id(report.getId())
                .type("INTERVIEW_REPORT")
                .status("SUCCESS")
                .userId(report.getUserId())
                .businessId(report.getSessionId())
                .summary(report.getSummary())
                .traceId(report.getTraceId())
                .createTime(report.getCreateTime())
                .updateTime(report.getUpdateTime())
                .build();
    }

    private CareerAdminTaskItemVO toExportTask(ResumeExportRecordDO record) {
        return CareerAdminTaskItemVO.builder()
                .id(record.getId())
                .type("EXPORT_RECORD")
                .status(record.getStatus())
                .userId(record.getUserId())
                .businessId(record.getResumeVersionId())
                .summary(record.getExportType() + " / " + valueOrDash(record.getTemplateVersion()))
                .failureReason(record.getErrorMessage())
                .traceId(record.getTraceId())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }

    private CareerAdminTaskItemVO toSingleFlightTask(CareerSingleFlightRecordDO record) {
        return CareerAdminTaskItemVO.builder()
                .id(record.getId())
                .type("SINGLE_FLIGHT")
                .status(record.getStatus())
                .runtimeStatus(record.getStatus())
                .userId(null)
                .businessId(record.getSingleFlightKey())
                .summary(record.getScene())
                .failureReason(record.getErrorType())
                .traceId(record.getTraceId())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .scene(record.getScene())
                .fencingToken(record.getFencingToken())
                .requestCount(record.getRequestCount())
                .build();
    }

    private Map<String, ResumeOptimizationReviewDO> latestReviewsByTaskId() {
        return selectLatestReviews(MAX_LIMIT).stream()
                .filter(review -> StrUtil.isNotBlank(review.getTaskId()))
                .collect(Collectors.toMap(ResumeOptimizationReviewDO::getTaskId, Function.identity(),
                        (left, right) -> latest(left.getUpdateTime(), right.getUpdateTime()).equals(left.getUpdateTime())
                                ? left
                                : right));
    }

    private List<ResumeDocumentDO> selectLatestResumeDocuments(int limit) {
        return resumeDocumentMapper.selectList(new LambdaQueryWrapper<ResumeDocumentDO>()
                .orderByDesc(ResumeDocumentDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<JobAlignmentReportDO> selectLatestAlignmentReports(int limit) {
        return jobAlignmentReportMapper.selectList(new LambdaQueryWrapper<JobAlignmentReportDO>()
                .orderByDesc(JobAlignmentReportDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<ResumeOptimizationTaskDO> selectLatestOptimizationTasks(int limit) {
        return resumeOptimizationTaskMapper.selectList(new LambdaQueryWrapper<ResumeOptimizationTaskDO>()
                .orderByDesc(ResumeOptimizationTaskDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<ResumeOptimizationReviewDO> selectLatestReviews(int limit) {
        return resumeOptimizationReviewMapper.selectList(new LambdaQueryWrapper<ResumeOptimizationReviewDO>()
                .orderByDesc(ResumeOptimizationReviewDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<InterviewSessionDO> selectLatestInterviewSessions(int limit) {
        return interviewSessionMapper.selectList(new LambdaQueryWrapper<InterviewSessionDO>()
                .orderByDesc(InterviewSessionDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<InterviewReportDO> selectLatestInterviewReports(int limit) {
        return interviewReportMapper.selectList(new LambdaQueryWrapper<InterviewReportDO>()
                .orderByDesc(InterviewReportDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<ResumeExportRecordDO> selectLatestExports(int limit) {
        return resumeExportRecordMapper.selectList(new LambdaQueryWrapper<ResumeExportRecordDO>()
                .orderByDesc(ResumeExportRecordDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<CareerSingleFlightRecordDO> selectLatestSingleFlightRecords(int limit) {
        return careerSingleFlightRecordMapper.selectList(new LambdaQueryWrapper<CareerSingleFlightRecordDO>()
                .orderByDesc(CareerSingleFlightRecordDO::getUpdateTime)
                .last("limit " + limit));
    }

    private List<CareerTaskAttemptDO> selectLatestTaskAttempts(int limit) {
        return careerTaskAttemptMapper.selectList(new LambdaQueryWrapper<CareerTaskAttemptDO>()
                .orderByDesc(CareerTaskAttemptDO::getUpdateTime)
                .last("limit " + limit));
    }

    private Long countReviews(String status) {
        return resumeOptimizationReviewMapper.selectCount(new LambdaQueryWrapper<ResumeOptimizationReviewDO>()
                .eq(ResumeOptimizationReviewDO::getStatus, status));
    }

    private Long countSingleFlight(String status) {
        return careerSingleFlightRecordMapper.selectCount(new LambdaQueryWrapper<CareerSingleFlightRecordDO>()
                .eq(CareerSingleFlightRecordDO::getStatus, status));
    }

    private Long countAttempts(String status) {
        return careerTaskAttemptMapper.selectCount(new LambdaQueryWrapper<CareerTaskAttemptDO>()
                .eq(CareerTaskAttemptDO::getStatus, status));
    }

    private Long countExports(String status) {
        return resumeExportRecordMapper.selectCount(new LambdaQueryWrapper<ResumeExportRecordDO>()
                .eq(ResumeExportRecordDO::getStatus, status));
    }

    private <T> Long countAll(Function<LambdaQueryWrapper<T>, Long> counter) {
        return counter.apply(new LambdaQueryWrapper<>());
    }

    private CareerAdminRubricDimensionVO dimension(String id, String name, Integer weight, List<String> signals) {
        return CareerAdminRubricDimensionVO.builder()
                .id(id)
                .name(name)
                .weight(weight)
                .signals(signals)
                .build();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalize(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : second;
    }

    private String readString(String json, String key) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Date latest(Date left, Date right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.after(right) ? left : right;
    }

    private String valueOrDash(Object value) {
        return Optional.ofNullable(value).map(String::valueOf).orElse("-");
    }
}
