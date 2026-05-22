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

package com.nageoffer.ai.ragent.career.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.request.CareerAlignmentCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerJobCreateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerAlignmentReportVO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.service.impl.JobAlignmentServiceImpl;
import com.nageoffer.ai.ragent.career.service.nlp.CareerNlpAnalysisResult;
import com.nageoffer.ai.ragent.career.service.nlp.CareerNlpEnrichmentService;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancementService;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEvidence;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEvidenceType;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalScenario;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAlignmentScoringTest {

    @Mock
    private JobDescriptionMapper jobDescriptionMapper;

    @Mock
    private JobAlignmentReportMapper jobAlignmentReportMapper;

    @Mock
    private ResumeVersionMapper resumeVersionMapper;

    @Mock
    private CareerJsonParser careerJsonParser;

    @Mock
    private CareerSingleFlightLlmService singleFlightLlmService;

    @Mock
    private CareerRetrievalEnhancementService careerRetrievalEnhancementService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobDescriptionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobAlignmentReportDO.class);
    }

    @Test
    void alignClampsScoreAbove100To100() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(resumeVersion());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(jobDescription());
        when(careerRetrievalEnhancementService.enhanceAlignment(any(ResumeVersionDO.class), any(JobDescriptionDO.class)))
                .thenReturn(defaultEnhancement());
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"score\":135}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(alignmentJson(135));
        doAnswer(invocation -> {
            JobAlignmentReportDO report = invocation.getArgument(0);
            report.setId("report-1");
            return 1;
        }).when(jobAlignmentReportMapper).insert(any(JobAlignmentReportDO.class));

        CareerAlignmentReportVO result = newService().align(alignmentRequest());

        assertEquals("report-1", result.getId());
        assertEquals(100, result.getScore());

        ArgumentCaptor<JobAlignmentReportDO> reportCaptor = ArgumentCaptor.forClass(JobAlignmentReportDO.class);
        verify(jobAlignmentReportMapper).insert(reportCaptor.capture());
        assertEquals(100, reportCaptor.getValue().getScore());
    }

    @Test
    void alignClampsScoreBelow0To0() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(resumeVersion());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(jobDescription());
        when(careerRetrievalEnhancementService.enhanceAlignment(any(ResumeVersionDO.class), any(JobDescriptionDO.class)))
                .thenReturn(defaultEnhancement());
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"score\":-8}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(alignmentJson(-8));
        doAnswer(invocation -> {
            JobAlignmentReportDO report = invocation.getArgument(0);
            report.setId("report-2");
            return 1;
        }).when(jobAlignmentReportMapper).insert(any(JobAlignmentReportDO.class));

        CareerAlignmentReportVO result = newService().align(alignmentRequest());

        assertEquals("report-2", result.getId());
        assertEquals(0, result.getScore());

        ArgumentCaptor<JobAlignmentReportDO> reportCaptor = ArgumentCaptor.forClass(JobAlignmentReportDO.class);
        verify(jobAlignmentReportMapper).insert(reportCaptor.capture());
        assertEquals(0, reportCaptor.getValue().getScore());
    }

    @Test
    void createJobRejectsRawTextShorterThan20BeforePersistence() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        CareerJobCreateRequest request = new CareerJobCreateRequest();
        request.setRawText("short jd");

        assertThrows(ClientException.class, () -> newService().createJob(request));

        verify(jobDescriptionMapper, never()).insert(any(JobDescriptionDO.class));
        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), anyString(), any(ChatRequest.class));
    }

    @Test
    void createJobBoundsDbConstrainedTextFieldsAndDefaultsMissingTitle() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        CareerJobCreateRequest request = new CareerJobCreateRequest();
        request.setCompany("c".repeat(150));
        request.setSourceType("s".repeat(40));
        request.setSourceLocation("l".repeat(600));
        request.setRawText("This is a Java backend role with Spring Boot and PostgreSQL.");
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(Map.of());

        newService().createJob(request);

        ArgumentCaptor<JobDescriptionDO> jobCaptor = ArgumentCaptor.forClass(JobDescriptionDO.class);
        verify(jobDescriptionMapper).insert(jobCaptor.capture());
        JobDescriptionDO job = jobCaptor.getValue();
        assertEquals("Untitled Job", job.getTitle());
        assertTrue(job.getCompany().length() <= 128);
        assertTrue(job.getSourceType().length() <= 32);
        assertTrue(job.getSourceLocation().length() <= 512);
    }

    @Test
    void createJobPersistsNlpEnrichmentIntoParsedJsonWhenProviderAvailable() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        CareerJobCreateRequest request = new CareerJobCreateRequest();
        request.setRawText("This Java backend role requires Spring Boot, Redis, PostgreSQL and RAG platform skills.");
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(new LinkedHashMap<>(Map.of(
                "title", "Java Backend Engineer")));
        JobAlignmentServiceImpl service = newService();
        service.setCareerNlpEnrichmentService(new CareerNlpEnrichmentService(
                nlpRequest -> new CareerNlpAnalysisResult("xunfei-nlp", "sid-jd",
                        List.of("Spring Boot", "RAG"), List.of("PostgreSQL"), "neutral")));

        service.createJob(request);

        ArgumentCaptor<JobDescriptionDO> jobCaptor = ArgumentCaptor.forClass(JobDescriptionDO.class);
        verify(jobDescriptionMapper).insert(jobCaptor.capture());
        assertTrue(jobCaptor.getValue().getParsedJson().contains("\"xunfeiNlp\""));
        assertTrue(jobCaptor.getValue().getParsedJson().contains("Spring Boot"));
        assertTrue(jobCaptor.getValue().getParsedJson().contains("PostgreSQL"));
    }

    @Test
    void alignRejectsMissingResumeVersionScopedByCurrentUser() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(null);

        assertThrows(ClientException.class, () -> newService().align(alignmentRequest()));

        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(resumeVersionMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("deleted"));
        verify(jobDescriptionMapper, never()).selectOne(anyJobDescriptionWrapper());
        verify(jobAlignmentReportMapper, never()).insert(any(JobAlignmentReportDO.class));
    }

    @Test
    void queryAlignmentRejectsReportWhenLinkedResumeOrJobIsDeleted() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(jobAlignmentReportMapper.selectOne(anyAlignmentReportWrapper()))
                .thenReturn(JobAlignmentReportDO.builder()
                        .id("report-1")
                        .userId("user-1")
                        .resumeVersionId("resume-1")
                        .jdId("jd-1")
                        .score(80)
                        .evidenceJson("[]")
                        .gapsJson("[]")
                        .risksJson("[]")
                        .build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(null);

        assertThrows(ClientException.class, () -> newService().queryAlignment("report-1"));
    }

    private JobAlignmentServiceImpl newService() {
        return new JobAlignmentServiceImpl(
                jobDescriptionMapper,
                jobAlignmentReportMapper,
                resumeVersionMapper,
                careerJsonParser,
                singleFlightLlmService,
                careerRetrievalEnhancementService
        );
    }

    private CareerRetrievalEnhancement defaultEnhancement() {
        return new CareerRetrievalEnhancement(CareerRetrievalScenario.ALIGNMENT, "query-only ideal candidate",
                List.of(CareerRetrievalEvidence.builder()
                        .type(CareerRetrievalEvidenceType.HYDE_QUERY)
                        .sourceId("test")
                        .text("query-only ideal candidate")
                        .score(1F)
                        .queryOnly(true)
                        .build()));
    }

    private CareerAlignmentCreateRequest alignmentRequest() {
        CareerAlignmentCreateRequest request = new CareerAlignmentCreateRequest();
        request.setResumeVersionId("resume-1");
        request.setJdId("jd-1");
        return request;
    }

    private ResumeVersionDO resumeVersion() {
        return ResumeVersionDO.builder()
                .id("resume-1")
                .userId("user-1")
                .contentJson("{\"skills\":[\"Java\"]}")
                .build();
    }

    private JobDescriptionDO jobDescription() {
        return JobDescriptionDO.builder()
                .id("jd-1")
                .userId("user-1")
                .parsedJson("{\"requiredSkills\":[\"Java\"]}")
                .build();
    }

    private Map<String, Object> alignmentJson(int score) {
        return Map.of(
                "score", score,
                "summary", "Strong match",
                "evidence", List.of(Map.of(
                        "jdRequirement", "Java",
                        "resumeEvidence", "Java backend experience",
                        "confidence", "HIGH"
                )),
                "gaps", List.of(),
                "risks", List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeVersionDO> anyResumeVersionWrapper() {
        return (Wrapper<ResumeVersionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<JobDescriptionDO> anyJobDescriptionWrapper() {
        return (Wrapper<JobDescriptionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<JobAlignmentReportDO> anyAlignmentReportWrapper() {
        return (Wrapper<JobAlignmentReportDO>) any(Wrapper.class);
    }
}
