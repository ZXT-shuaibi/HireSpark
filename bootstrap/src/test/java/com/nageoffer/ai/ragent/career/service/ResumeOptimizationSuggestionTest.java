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
import com.nageoffer.ai.ragent.career.controller.request.CareerOptimizationCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerSuggestionDecisionRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationTaskVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeVersionVO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerProgressEventDO;
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
import com.nageoffer.ai.ragent.career.service.impl.ResumeOptimizationServiceImpl;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.progress.CareerProgressStreamService;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeOptimizationSuggestionTest {

    @Mock
    private ResumeOptimizationTaskMapper taskMapper;

    @Mock
    private ResumeOptimizationSuggestionMapper suggestionMapper;

    @Mock
    private ResumeOptimizationReviewMapper reviewMapper;

    @Mock
    private CareerProgressEventMapper progressEventMapper;

    @Mock
    private ResumeVersionMapper resumeVersionMapper;

    @Mock
    private JobDescriptionMapper jobDescriptionMapper;

    @Mock
    private JobAlignmentReportMapper alignmentReportMapper;

    @Mock
    private CareerJsonParser careerJsonParser;

    @Mock
    private CareerSingleFlightLlmService singleFlightLlmService;

    @Mock
    private CareerRetrievalEnhancementService careerRetrievalEnhancementService;

    @Mock
    private CareerProgressStreamService careerProgressStreamService;

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeOptimizationTaskDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeOptimizationSuggestionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeOptimizationReviewDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerProgressEventDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobDescriptionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobAlignmentReportDO.class);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void acceptedStatusIsStableEnumContract() {
        assertEquals(
                List.of("PENDING", "ACCEPTED", "REJECTED", "EDITED"),
                Arrays.stream(ResumeSuggestionStatus.values()).map(Enum::name).toList()
        );
    }

    @Test
    void decideSuggestionRejectsInvalidStatusBeforeUpdate() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(suggestionMapper.selectOne(anySuggestionWrapper())).thenReturn(ResumeOptimizationSuggestionDO.builder()
                .id("suggestion-1")
                .userId("user-1")
                .taskId("task-1")
                .status(ResumeSuggestionStatus.PENDING.name())
                .suggestedText("Keep this")
                .build());
        CareerSuggestionDecisionRequest request = new CareerSuggestionDecisionRequest();
        request.setStatus("ARCHIVED");

        assertThrows(ClientException.class, () -> newService().decideSuggestion("suggestion-1", request));

        verify(suggestionMapper, never()).updateById(any(ResumeOptimizationSuggestionDO.class));
    }

    @Test
    void decideSuggestionRejectsWhenParentTaskLinkedResumeIsDeleted() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(suggestionMapper.selectOne(anySuggestionWrapper())).thenReturn(suggestion(
                "suggestion-1", ResumeSuggestionStatus.PENDING, "Built APIs", "Built Spring APIs"));
        when(taskMapper.selectOne(anyTaskWrapper())).thenReturn(ResumeOptimizationTaskDO.builder()
                .id("task-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status(CareerTaskStatus.SUCCESS.name())
                .outputJson("{\"summary\":\"ok\"}")
                .inputJson("{\"alignmentReportId\":\"alignment-1\"}")
                .build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(null);
        CareerSuggestionDecisionRequest request = new CareerSuggestionDecisionRequest();
        request.setStatus("ACCEPTED");

        assertThrows(ClientException.class, () -> newService().decideSuggestion("suggestion-1", request));

        verify(suggestionMapper, never()).updateById(any(ResumeOptimizationSuggestionDO.class));
    }

    @Test
    void createTaskClampsPersistsSuggestionsAsPendingAndMarksTaskSuccessOnHappyPath() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(resumeVersion());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(jobDescription());
        when(careerRetrievalEnhancementService.enhanceOptimization(any(ResumeVersionDO.class),
                any(JobDescriptionDO.class), anyString())).thenReturn(defaultEnhancement());
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"summary\":\"ok\"}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(Map.of(
                "summary", "Add proof points",
                "optimizedResume", Map.of("basic", Map.of("name", "Alice")),
                "suggestions", List.of(
                        Map.of(
                                "category", "c".repeat(80),
                                "title", "t".repeat(160),
                                "originalText", "Built APIs",
                                "suggestedText", "Built Spring APIs with latency metrics",
                                "reason", "Adds measurable impact",
                                "riskLevel", "MEDIUM".repeat(8)
                        ),
                        Map.of(
                                "category", "skills",
                                "title", "Add PostgreSQL",
                                "originalText", "SQL",
                                "suggestedText", "PostgreSQL",
                                "reason", "Matches JD",
                                "riskLevel", "LOW"
                        )
                )
        )).thenReturn(Map.of("qualityScore", 0.82D, "truthfulnessRisk", false));
        doAnswer(invocation -> {
            ResumeOptimizationTaskDO task = invocation.getArgument(0);
            task.setId("task-1");
            return 1;
        }).when(taskMapper).insert(any(ResumeOptimizationTaskDO.class));
        AtomicInteger suggestionIndex = new AtomicInteger();
        doAnswer(invocation -> {
            ResumeOptimizationSuggestionDO suggestion = invocation.getArgument(0);
            suggestion.setId("suggestion-" + suggestionIndex.incrementAndGet());
            return 1;
        }).when(suggestionMapper).insert(any(ResumeOptimizationSuggestionDO.class));

        CareerOptimizationTaskVO result = newService().createTask(createRequest());

        assertEquals("task-1", result.getId());
        assertEquals(CareerTaskStatus.SUCCESS.name(), result.getStatus());
        assertEquals("resume-1", result.getResumeVersionId());
        assertEquals("jd-1", result.getJdId());
        assertEquals("Add proof points", result.getSummary());
        assertEquals(2, result.getSuggestions().size());
        assertEquals(ResumeSuggestionStatus.PENDING.name(), result.getSuggestions().get(0).getStatus());

        ArgumentCaptor<ResumeOptimizationTaskDO> taskUpdateCaptor = ArgumentCaptor.forClass(ResumeOptimizationTaskDO.class);
        verify(taskMapper, times(2)).updateById(taskUpdateCaptor.capture());
        ResumeOptimizationTaskDO finalTask = taskUpdateCaptor.getAllValues().getLast();
        assertEquals(CareerTaskStatus.SUCCESS.name(), finalTask.getStatus());
        assertNotNull(finalTask.getOutputJson());

        ArgumentCaptor<ResumeOptimizationSuggestionDO> suggestionCaptor =
                ArgumentCaptor.forClass(ResumeOptimizationSuggestionDO.class);
        verify(suggestionMapper, org.mockito.Mockito.times(2)).insert(suggestionCaptor.capture());
        ResumeOptimizationSuggestionDO first = suggestionCaptor.getAllValues().get(0);
        assertEquals(ResumeSuggestionStatus.PENDING.name(), first.getStatus());
        assertTrue(first.getCategory().length() <= 64);
        assertTrue(first.getTitle().length() <= 128);
        assertTrue(first.getRiskLevel().length() <= 32);
    }

    @Test
    void createTaskDeletesPartialSuggestionsAndNeverMarksSuccessWhenSuggestionInsertFails() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(resumeVersion());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(jobDescription());
        when(careerRetrievalEnhancementService.enhanceOptimization(any(ResumeVersionDO.class),
                any(JobDescriptionDO.class), anyString())).thenReturn(defaultEnhancement());
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"summary\":\"ok\"}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(Map.of(
                "summary", "Add proof points",
                "suggestions", List.of(
                        Map.of(
                                "category", "impact",
                                "title", "Improve wording",
                                "originalText", "Built APIs",
                                "suggestedText", "Built Spring APIs",
                                "reason", "Better match",
                                "riskLevel", "LOW"
                        ),
                        Map.of(
                                "category", "skills",
                                "title", "Add PostgreSQL",
                                "originalText", "SQL",
                                "suggestedText", "PostgreSQL",
                                "reason", "Matches JD",
                                "riskLevel", "LOW"
                        )
                )
        ));
        doAnswer(invocation -> {
            ResumeOptimizationTaskDO task = invocation.getArgument(0);
            task.setId("task-1");
            return 1;
        }).when(taskMapper).insert(any(ResumeOptimizationTaskDO.class));
        List<String> taskUpdateStatuses = new ArrayList<>();
        doAnswer(invocation -> {
            ResumeOptimizationTaskDO task = invocation.getArgument(0);
            taskUpdateStatuses.add(task.getStatus());
            return 1;
        }).when(taskMapper).updateById(any(ResumeOptimizationTaskDO.class));
        AtomicInteger suggestionIndex = new AtomicInteger();
        doAnswer(invocation -> {
            ResumeOptimizationSuggestionDO suggestion = invocation.getArgument(0);
            int index = suggestionIndex.incrementAndGet();
            suggestion.setId("suggestion-" + index);
            if (index == 2) {
                throw new RuntimeException("suggestion insert failed");
            }
            return 1;
        }).when(suggestionMapper).insert(any(ResumeOptimizationSuggestionDO.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> newService().createTask(createRequest()));

        assertEquals("suggestion insert failed", ex.getMessage());
        verify(suggestionMapper).deleteById("suggestion-1");
        verify(taskMapper, times(2)).updateById(any(ResumeOptimizationTaskDO.class));
        assertEquals(CareerTaskStatus.FAILED.name(), taskUpdateStatuses.getLast());
    }

    @Test
    void createTaskRejectsMissingJdIdBeforePersistence() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        CareerOptimizationCreateRequest request = new CareerOptimizationCreateRequest();
        request.setResumeVersionId("resume-1");

        assertThrows(ClientException.class, () -> newService().createTask(request));

        verify(taskMapper, never()).insert(any(ResumeOptimizationTaskDO.class));
        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), anyString(), any(ChatRequest.class));
    }

    @Test
    void queryTaskRejectsWhenLinkedResumeIsDeleted() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(taskMapper.selectOne(anyTaskWrapper())).thenReturn(ResumeOptimizationTaskDO.builder()
                .id("task-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status(CareerTaskStatus.SUCCESS.name())
                .outputJson("{\"summary\":\"ok\"}")
                .inputJson("{\"alignmentReportId\":\"alignment-1\"}")
                .build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(null);

        assertThrows(ClientException.class, () -> newService().queryTask("task-1"));

        verify(suggestionMapper, never()).selectList(anySuggestionWrapper());
    }

    @Test
    void generateVersionFromAcceptedSuggestionsIncrementsVersionNoAndUsesAcceptedEditedSuggestionText() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(taskMapper.selectOne(anyTaskWrapper())).thenReturn(ResumeOptimizationTaskDO.builder()
                .id("task-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status(CareerTaskStatus.SUCCESS.name())
                .outputJson("{\"optimizedResume\":{\"basic\":{\"name\":\"Alice\"}}}")
                .build());
        when(reviewMapper.selectList(anyReviewWrapper())).thenReturn(List.of(passedReview()));
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(resumeVersion());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(jobDescription());
        when(resumeVersionMapper.selectList(anyResumeVersionWrapper())).thenReturn(List.of(
                ResumeVersionDO.builder().versionNo(1).build(),
                ResumeVersionDO.builder().versionNo(4).build()
        ));
        when(suggestionMapper.selectList(anySuggestionWrapper())).thenReturn(List.of(
                suggestion("suggestion-1", ResumeSuggestionStatus.ACCEPTED, "Built APIs", "Built Spring APIs"),
                suggestion("suggestion-2", ResumeSuggestionStatus.EDITED, "SQL", "PostgreSQL"),
                suggestion("suggestion-3", ResumeSuggestionStatus.REJECTED, "Keep this", "Do not apply")
        ));
        doAnswer(invocation -> {
            ResumeVersionDO version = invocation.getArgument(0);
            version.setId("version-5");
            version.setCreateTime(new Date(1700000000000L));
            return 1;
        }).when(resumeVersionMapper).insert(any(ResumeVersionDO.class));

        CareerResumeVersionVO result = newService().generateVersionFromAcceptedSuggestions("task-1");

        assertEquals("version-5", result.getId());
        assertEquals("profile-1", result.getProfileId());
        assertEquals(5, result.getVersionNo());
        assertTrue(result.getContent().contains("\"markdownContent\":\"Built Spring APIs with PostgreSQL. Keep this.\""));
        assertTrue(result.getContent().contains("\"optimizationTaskId\":\"task-1\""));
        assertTrue(result.getContent().contains("\"suggestedText\":\"Built Spring APIs\""));
        assertEquals("Built Spring APIs with PostgreSQL. Keep this.", result.getMarkdownContent());

        ArgumentCaptor<ResumeVersionDO> versionCaptor = ArgumentCaptor.forClass(ResumeVersionDO.class);
        verify(resumeVersionMapper).insert(versionCaptor.capture());
        assertEquals("OPTIMIZED", versionCaptor.getValue().getSourceType());
        assertEquals(5, versionCaptor.getValue().getVersionNo());
    }

    @Test
    void generateVersionRejectsWhenNoAcceptedOrEditedSuggestions() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(taskMapper.selectOne(anyTaskWrapper())).thenReturn(ResumeOptimizationTaskDO.builder()
                .id("task-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status(CareerTaskStatus.SUCCESS.name())
                .outputJson("{\"optimizedResume\":{\"basic\":{\"name\":\"Alice\"}}}")
                .build());
        when(reviewMapper.selectList(anyReviewWrapper())).thenReturn(List.of(passedReview()));
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(resumeVersion());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(jobDescription());
        when(suggestionMapper.selectList(anySuggestionWrapper())).thenReturn(List.of());

        assertThrows(ClientException.class, () -> newService().generateVersionFromAcceptedSuggestions("task-1"));

        verify(resumeVersionMapper, never()).insert(any(ResumeVersionDO.class));
    }

    private ResumeOptimizationServiceImpl newService() {
        return new ResumeOptimizationServiceImpl(
                taskMapper,
                suggestionMapper,
                reviewMapper,
                progressEventMapper,
                resumeVersionMapper,
                jobDescriptionMapper,
                alignmentReportMapper,
                careerJsonParser,
                singleFlightLlmService,
                careerRetrievalEnhancementService,
                careerProgressStreamService
        );
    }

    private CareerOptimizationCreateRequest createRequest() {
        CareerOptimizationCreateRequest request = new CareerOptimizationCreateRequest();
        request.setResumeVersionId("resume-1");
        request.setJdId("jd-1");
        return request;
    }

    private ResumeVersionDO resumeVersion() {
        return ResumeVersionDO.builder()
                .id("resume-1")
                .userId("user-1")
                .profileId("profile-1")
                .documentId("document-1")
                .title("Alice Resume")
                .versionNo(4)
                .sourceType("PARSED")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .markdownContent("Built APIs with SQL. Keep this.")
                .build();
    }

    private JobDescriptionDO jobDescription() {
        return JobDescriptionDO.builder()
                .id("jd-1")
                .userId("user-1")
                .parsedJson("{\"requiredSkills\":[\"Java\",\"PostgreSQL\"]}")
                .build();
    }

    private ResumeOptimizationReviewDO passedReview() {
        return ResumeOptimizationReviewDO.builder()
                .id("review-1")
                .taskId("task-1")
                .userId("user-1")
                .status(OptimizationReviewStatus.PASSED.name())
                .reviewerOutputJson("{\"riskSummary\":\"ok\"}")
                .build();
    }

    private CareerRetrievalEnhancement defaultEnhancement() {
        return new CareerRetrievalEnhancement(CareerRetrievalScenario.OPTIMIZATION, "query-only gap evidence",
                List.of(CareerRetrievalEvidence.builder()
                        .type(CareerRetrievalEvidenceType.HYDE_QUERY)
                        .sourceId("test")
                        .text("query-only gap evidence")
                        .score(1F)
                        .queryOnly(true)
                        .build()));
    }

    private ResumeOptimizationSuggestionDO suggestion(String id,
                                                      ResumeSuggestionStatus status,
                                                      String originalText,
                                                      String suggestedText) {
        return ResumeOptimizationSuggestionDO.builder()
                .id(id)
                .taskId("task-1")
                .userId("user-1")
                .category("impact")
                .title("Improve wording")
                .originalText(originalText)
                .suggestedText(suggestedText)
                .reason("Better match")
                .riskLevel("LOW")
                .status(status.name())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeOptimizationTaskDO> anyTaskWrapper() {
        return (Wrapper<ResumeOptimizationTaskDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeOptimizationReviewDO> anyReviewWrapper() {
        return (Wrapper<ResumeOptimizationReviewDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeOptimizationSuggestionDO> anySuggestionWrapper() {
        return (Wrapper<ResumeOptimizationSuggestionDO>) any(Wrapper.class);
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
