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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.request.CareerAlignmentCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerJobCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerJobUrlImportRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerAlignmentReportVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerJobVO;
import com.nageoffer.ai.ragent.career.crawler.JobPostingCrawlResult;
import com.nageoffer.ai.ragent.career.crawler.JobPostingCrawler;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.service.JobAlignmentService;
import com.nageoffer.ai.ragent.career.service.nlp.CareerNlpEnrichmentService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobAlignmentServiceImpl implements JobAlignmentService {

    private static final String SOURCE_TYPE_MANUAL = "MANUAL";
    private static final String SOURCE_TYPE_URL = "URL";
    private static final String DEFAULT_JOB_TITLE = "Untitled Job";
    private static final int RAW_TEXT_MIN_LENGTH = 20;
    private static final int RAW_TEXT_MAX_LENGTH = 20000;
    private static final int TITLE_MAX_LENGTH = 128;
    private static final int COMPANY_MAX_LENGTH = 128;
    private static final int SOURCE_TYPE_MAX_LENGTH = 32;
    private static final int SOURCE_LOCATION_MAX_LENGTH = 512;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobAlignmentReportMapper jobAlignmentReportMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final CareerJsonParser careerJsonParser;
    private final CareerSingleFlightLlmService singleFlightLlmService;
    private final CareerRetrievalEnhancementService careerRetrievalEnhancementService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JobPostingCrawler jobPostingCrawler;
    private CareerNlpEnrichmentService careerNlpEnrichmentService;

    @Autowired(required = false)
    public void setJobPostingCrawler(JobPostingCrawler jobPostingCrawler) {
        this.jobPostingCrawler = jobPostingCrawler;
    }

    @Autowired(required = false)
    public void setCareerNlpEnrichmentService(CareerNlpEnrichmentService careerNlpEnrichmentService) {
        this.careerNlpEnrichmentService = careerNlpEnrichmentService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerJobVO createJob(CareerJobCreateRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Job create request is required");
        }
        String rawText = normalizeRawText(request.getRawText());
        String traceId = "career-jd-parse-" + stableRequestToken(userId, rawText);
        String response = singleFlightLlmService.chat("JD_PARSE",
                buildSingleFlightKey("JD_PARSE", userId, stableRequestToken(userId, rawText), rawText),
                traceId,
                ChatRequest.builder()
                .messages(List.of(ChatMessage.user(String.format(CareerPromptTemplates.JD_PARSE, rawText))))
                .temperature(0.1D)
                .thinking(false)
                .build());
        Map<String, Object> parsed = careerJsonParser.parseObject(response);
        enrichWithNlp(parsed, CareerNlpEnrichmentService.SCENE_JD_PARSE, rawText, traceId);

        JobDescriptionDO job = JobDescriptionDO.builder()
                .userId(userId)
                .title(limitText(firstNotBlank(trimToNull(request.getTitle()), extractString(parsed.get("title"))),
                        DEFAULT_JOB_TITLE, TITLE_MAX_LENGTH))
                .company(limitText(firstNotBlank(trimToNull(request.getCompany()), extractString(parsed.get("company"))),
                        null, COMPANY_MAX_LENGTH))
                .sourceType(limitText(defaultSourceType(request.getSourceType()), SOURCE_TYPE_MANUAL, SOURCE_TYPE_MAX_LENGTH))
                .sourceLocation(limitText(trimToNull(request.getSourceLocation()), null, SOURCE_LOCATION_MAX_LENGTH))
                .rawText(rawText)
                .parsedJson(writeJson(parsed, "Failed to serialize job description JSON"))
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        jobDescriptionMapper.insert(job);
        return toJobVO(job, parsed);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerJobVO importJobFromUrl(CareerJobUrlImportRequest request) {
        if (request == null || StrUtil.isBlank(request.getUrl())) {
            throw new ClientException("Job URL is required");
        }
        if (jobPostingCrawler == null) {
            throw new ServiceException("Job posting crawler is not available");
        }
        String url = request.getUrl().trim();
        JobPostingCrawlResult crawlResult = jobPostingCrawler.crawl(url);
        if (crawlResult == null || StrUtil.isBlank(crawlResult.rawText())) {
            throw new ServiceException("Failed to crawl job description from URL");
        }
        CareerJobCreateRequest createRequest = new CareerJobCreateRequest();
        createRequest.setTitle(crawlResult.title());
        createRequest.setCompany(crawlResult.company());
        createRequest.setRawText(crawlResult.rawText());
        createRequest.setSourceType(SOURCE_TYPE_URL);
        createRequest.setSourceLocation(url);
        return createJob(createRequest);
    }

    @Override
    public CareerJobVO queryJob(String jdId) {
        JobDescriptionDO job = requireJob(jdId, requireUserId());
        return toJobVO(job, readMap(job.getParsedJson(), "Failed to parse job description JSON"));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerAlignmentReportVO align(CareerAlignmentCreateRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Alignment request is required");
        }
        if (StrUtil.isBlank(request.getResumeVersionId())) {
            throw new ClientException("Resume version id is required");
        }
        if (StrUtil.isBlank(request.getJdId())) {
            throw new ClientException("Job description id is required");
        }

        ResumeVersionDO resumeVersion = requireResumeVersion(request.getResumeVersionId(), userId);
        JobDescriptionDO job = requireJob(request.getJdId(), userId);
        if (StrUtil.isBlank(resumeVersion.getContentJson())) {
            throw new ClientException("Resume content JSON is required");
        }
        if (StrUtil.isBlank(job.getParsedJson())) {
            throw new ClientException("Job description JSON is required");
        }

        CareerRetrievalEnhancement enhancement =
                careerRetrievalEnhancementService.enhanceAlignment(resumeVersion, job);
        String prompt = appendRetrievalEvidence(
                String.format(CareerPromptTemplates.JD_ALIGNMENT, resumeVersion.getContentJson(), job.getParsedJson()),
                enhancement);
        String traceId = "career-alignment-" + resumeVersion.getId() + "-" + job.getId();
        String response = singleFlightLlmService.chat("JD_ALIGNMENT",
                buildSingleFlightKey("JD_ALIGNMENT", userId, resumeVersion.getId() + ":" + job.getId(), prompt),
                traceId,
                ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1D)
                .thinking(false)
                .build());
        Map<String, Object> alignment = careerJsonParser.parseObject(response);
        int score = clampScore(readScore(alignment.get("score")));
        List<Object> evidence = toList(alignment.get("evidence"));
        List<Object> gaps = toList(alignment.get("gaps"));
        List<Object> risks = toList(alignment.get("risks"));

        JobAlignmentReportDO report = JobAlignmentReportDO.builder()
                .userId(userId)
                .resumeVersionId(resumeVersion.getId())
                .jdId(job.getId())
                .score(score)
                .summary(extractString(alignment.get("summary")))
                .evidenceJson(writeJson(evidence, "Failed to serialize alignment evidence JSON"))
                .gapsJson(writeJson(gaps, "Failed to serialize alignment gaps JSON"))
                .risksJson(writeJson(risks, "Failed to serialize alignment risks JSON"))
                .traceId(traceId)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        jobAlignmentReportMapper.insert(report);
        return toReportVO(report, evidence, gaps, risks);
    }

    @Override
    public CareerAlignmentReportVO queryAlignment(String reportId) {
        String userId = requireUserId();
        if (StrUtil.isBlank(reportId)) {
            throw new ClientException("Alignment report id is required");
        }
        JobAlignmentReportDO report = jobAlignmentReportMapper.selectOne(
                Wrappers.lambdaQuery(JobAlignmentReportDO.class)
                        .eq(JobAlignmentReportDO::getId, reportId)
                        .eq(JobAlignmentReportDO::getUserId, userId)
                        .eq(JobAlignmentReportDO::getDeleted, 0)
        );
        if (report == null) {
            throw new ClientException("Alignment report does not exist");
        }
        ensureAlignmentLinksVisible(report, userId);
        return toReportVO(report);
    }

    private ResumeVersionDO requireResumeVersion(String resumeVersionId, String userId) {
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

    private void ensureAlignmentLinksVisible(JobAlignmentReportDO report, String userId) {
        ResumeVersionDO resumeVersion = resumeVersionMapper.selectOne(
                Wrappers.lambdaQuery(ResumeVersionDO.class)
                        .eq(ResumeVersionDO::getId, report.getResumeVersionId())
                        .eq(ResumeVersionDO::getUserId, userId)
                        .eq(ResumeVersionDO::getDeleted, 0)
        );
        JobDescriptionDO job = jobDescriptionMapper.selectOne(
                Wrappers.lambdaQuery(JobDescriptionDO.class)
                        .eq(JobDescriptionDO::getId, report.getJdId())
                        .eq(JobDescriptionDO::getUserId, userId)
                        .eq(JobDescriptionDO::getDeleted, 0)
        );
        if (resumeVersion == null || job == null) {
            throw new ClientException("Alignment report does not exist");
        }
    }

    private CareerJobVO toJobVO(JobDescriptionDO job, Map<String, Object> parsed) {
        return CareerJobVO.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .sourceType(job.getSourceType())
                .sourceLocation(job.getSourceLocation())
                .rawText(job.getRawText())
                .parsed(parsed)
                .createTime(job.getCreateTime())
                .build();
    }

    private CareerAlignmentReportVO toReportVO(JobAlignmentReportDO report) {
        return toReportVO(
                report,
                readList(report.getEvidenceJson(), "Failed to parse alignment evidence JSON"),
                readList(report.getGapsJson(), "Failed to parse alignment gaps JSON"),
                readList(report.getRisksJson(), "Failed to parse alignment risks JSON")
        );
    }

    private CareerAlignmentReportVO toReportVO(JobAlignmentReportDO report,
                                               List<Object> evidence,
                                               List<Object> gaps,
                                               List<Object> risks) {
        return CareerAlignmentReportVO.builder()
                .id(report.getId())
                .resumeVersionId(report.getResumeVersionId())
                .jdId(report.getJdId())
                .score(report.getScore())
                .summary(report.getSummary())
                .evidence(evidence)
                .gaps(gaps)
                .risks(risks)
                .traceId(report.getTraceId())
                .build();
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }

    private String normalizeRawText(String rawText) {
        String text = trimToNull(rawText);
        if (text == null) {
            throw new ClientException("Job description raw text is required");
        }
        if (text.length() < RAW_TEXT_MIN_LENGTH || text.length() > RAW_TEXT_MAX_LENGTH) {
            throw new ClientException("Job description raw text length must be between 20 and 20000");
        }
        return text;
    }

    private String defaultSourceType(String sourceType) {
        String value = trimToNull(sourceType);
        return value == null ? SOURCE_TYPE_MANUAL : value;
    }

    private int readScore(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                return Double.valueOf(text.trim()).intValue();
            } catch (NumberFormatException ex) {
                throw new ClientException("Alignment score is invalid");
            }
        }
        throw new ClientException("Alignment score is required");
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(value);
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
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

    private List<Object> readList(String value, String errorMessage) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, LIST_TYPE);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
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

    private String firstNotBlank(String preferred, String fallback) {
        return StrUtil.isNotBlank(preferred) ? preferred : fallback;
    }

    private String limitText(String value, String fallback, int maxLength) {
        String text = firstNotBlank(trimToNull(value), fallback);
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String appendRetrievalEvidence(String prompt, CareerRetrievalEnhancement enhancement) {
        if (enhancement == null) {
            return prompt;
        }
        return prompt + "\n\nCareer retrieval evidence JSON:\n"
                + writeJson(enhancement.toPromptPayload(), "Failed to serialize retrieval evidence JSON")
                + "\nRules: HYDE_QUERY evidence is QUERY_ONLY. Use it for retrieval context only; never treat it as resume fact.";
    }

    private void enrichWithNlp(Map<String, Object> payload, String scene, String text, String traceId) {
        if (payload == null || careerNlpEnrichmentService == null) {
            return;
        }
        Map<String, Object> nlp = careerNlpEnrichmentService.enrich(scene, text, traceId);
        if (!nlp.isEmpty()) {
            payload.put(CareerNlpEnrichmentService.PAYLOAD_KEY, nlp);
        }
    }

    private String buildSingleFlightKey(String scene, String userId, String artifactId, String prompt) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "CAREER_AI"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"),
                StrUtil.blankToDefault(prompt, ""));
    }

    private String stableRequestToken(String userId, String value) {
        int hash = (StrUtil.blankToDefault(userId, "anonymous") + ":"
                + StrUtil.blankToDefault(value, "")).hashCode();
        return Integer.toUnsignedString(hash).toLowerCase(Locale.ROOT);
    }
}
