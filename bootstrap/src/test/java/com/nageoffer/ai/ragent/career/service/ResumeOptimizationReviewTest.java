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
import com.nageoffer.ai.ragent.career.controller.vo.CareerProgressEventVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationTaskVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class ResumeOptimizationReviewTest {

    private final ResumeOptimizationReviewEvaluator evaluator = new ResumeOptimizationReviewEvaluator();

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
    void qualityScore082WithoutRiskPasses() {
        ResumeOptimizationReviewEvaluator.Decision decision = evaluator.evaluate(
                Map.of("qualityScore", 0.82D),
                List.of(lowRiskSuggestion())
        );

        assertEquals(OptimizationReviewStatus.PASSED, decision.status());
        assertEquals(0.82D, decision.qualityScore());
        assertFalse(decision.truthfulnessRisk());
    }

    @Test
    void qualityScore079WithoutRiskNeedsRevision() {
        ResumeOptimizationReviewEvaluator.Decision decision = evaluator.evaluate(
                Map.of("qualityScore", 0.79D),
                List.of(lowRiskSuggestion())
        );

        assertEquals(OptimizationReviewStatus.NEEDS_REVISION, decision.status());
        assertEquals(0.79D, decision.qualityScore());
        assertFalse(decision.truthfulnessRisk());
    }

    @Test
    void qualityScore080WithoutRiskNeedsRevision() {
        ResumeOptimizationReviewEvaluator.Decision decision = evaluator.evaluate(
                Map.of("qualityScore", 0.80D),
                List.of(lowRiskSuggestion())
        );

        assertEquals(OptimizationReviewStatus.NEEDS_REVISION, decision.status());
        assertEquals(0.80D, decision.qualityScore());
        assertFalse(decision.truthfulnessRisk());
    }

    @Test
    void qualityScore093WithTruthfulnessRiskIsBlocked() {
        ResumeOptimizationReviewEvaluator.Decision decision = evaluator.evaluate(
                Map.of("qualityScore", 0.93D, "truthfulnessRisk", true),
                List.of(lowRiskSuggestion())
        );

        assertEquals(OptimizationReviewStatus.BLOCKED_BY_RISK, decision.status());
        assertEquals(0.93D, decision.qualityScore());
        assertTrue(decision.truthfulnessRisk());
    }

    @Test
    void highRiskSuggestionBlocksWhenReviewerFieldsAreMissing() {
        ResumeOptimizationReviewEvaluator.Decision decision = evaluator.evaluate(
                Map.of(),
                List.of(ResumeOptimizationSuggestionDO.builder().riskLevel("HIGH").build())
        );

        assertEquals(OptimizationReviewStatus.BLOCKED_BY_RISK, decision.status());
        assertTrue(decision.truthfulnessRisk());
    }

    @Test
    void createTaskReturnsReviewStatusAndProgressEvents() {
        setupCreateTaskMocks(reviewerOutput(0.82D, false, List.of(), List.of("suggestion-1"), List.of()));

        CareerOptimizationTaskVO result = newService().createTask(createRequest());

        verify(singleFlightLlmService, times(2)).chat(anyString(), anyString(), anyString(), any(ChatRequest.class));
        assertEquals(CareerTaskStatus.SUCCESS.name(), result.getStatus());
        assertEquals("PASSED", result.getReviewStatus());
        assertEquals(0, result.getQualityScore().compareTo(java.math.BigDecimal.valueOf(0.82D)));
        assertEquals(List.of("GENERATING", "REVIEWING", "PASSED"), progressTypes(result));
        assertEquals("career-opt-task-1", result.getTraceId());
        verify(careerProgressStreamService, times(3)).publishOptimization(any(CareerProgressEventDO.class));

        ArgumentCaptor<ResumeOptimizationTaskDO> taskCaptor =
                ArgumentCaptor.forClass(ResumeOptimizationTaskDO.class);
        verify(taskMapper, times(2)).updateById(taskCaptor.capture());
        ResumeOptimizationTaskDO finalTask = taskCaptor.getAllValues().getLast();
        assertEquals(CareerTaskStatus.SUCCESS.name(), finalTask.getStatus());
        assertEquals("career-opt-task-1", finalTask.getTraceId());

        ArgumentCaptor<ResumeOptimizationReviewDO> reviewCaptor =
                ArgumentCaptor.forClass(ResumeOptimizationReviewDO.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        assertEquals("PASSED", reviewCaptor.getValue().getStatus());
        assertEquals("career-opt-task-1", reviewCaptor.getValue().getTraceId());
        assertTrue(reviewCaptor.getValue().getReviewerOutputJson().contains("revisionInstructions"));
        assertTrue(reviewCaptor.getValue().getReviewerOutputJson().contains("acceptedSuggestionIds"));
    }

    @Test
    void reviewerLowScoreMarksTaskNeedsReviewAndBlocksVersionGeneration() {
        CreateTaskState state = setupCreateTaskMocks(
                List.of(executorOutput(), executorOutput(), executorOutput()),
                List.of(
                        reviewerOutput(0.80D, false, List.of(), List.of(), List.of("suggestion-1")),
                        reviewerOutput(0.79D, false, List.of(), List.of(), List.of("suggestion-1")),
                        reviewerOutput(0.78D, false, List.of(), List.of(), List.of("suggestion-1"))
                )
        );

        CareerOptimizationTaskVO result = newService().createTask(createRequest());

        assertEquals(CareerTaskStatus.NEEDS_REVIEW.name(), result.getStatus());
        assertEquals(OptimizationReviewStatus.NEEDS_REVISION.name(), result.getReviewStatus());
        assertEquals(List.of("GENERATING", "REVIEWING", "REVISING", "GENERATING",
                "REVIEWING", "REVISING", "GENERATING", "REVIEWING", "NEEDS_REVIEW"), progressTypes(result));
        assertEquals(List.of(1, 2, 3), state.reviews.stream().map(ResumeOptimizationReviewDO::getIterationNo).toList());

        when(taskMapper.selectOne(anyTaskWrapper())).thenAnswer(invocation -> state.task.get());

        assertThrows(ClientException.class,
                () -> newService().generateVersionFromAcceptedSuggestions("task-1"));
        verify(resumeVersionMapper, never()).insert(any(ResumeVersionDO.class));
    }

    @Test
    void lowScoreTriggersSecondExecutorIterationAndPersistsFinalSuggestionsOnly() {
        Map<String, Object> firstOutput = executorOutput("First draft", "Built reliable APIs");
        Map<String, Object> secondOutput = executorOutput("Second draft", "Built reliable Spring APIs with latency metrics");
        CreateTaskState state = setupCreateTaskMocks(
                List.of(firstOutput, secondOutput),
                List.of(
                        reviewerOutput(0.72D, false, List.of(), List.of(), List.of("suggestion-1")),
                        reviewerOutput(0.86D, false, List.of(), List.of("suggestion-1"), List.of())
                )
        );

        CareerOptimizationTaskVO result = newService().createTask(createRequest());

        verify(singleFlightLlmService, times(4)).chat(anyString(), anyString(), anyString(), any(ChatRequest.class));
        assertEquals(CareerTaskStatus.SUCCESS.name(), result.getStatus());
        assertEquals(OptimizationReviewStatus.PASSED.name(), result.getReviewStatus());
        assertEquals("Second draft", result.getSummary());
        assertEquals(List.of("GENERATING", "REVIEWING", "REVISING", "GENERATING", "REVIEWING", "PASSED"),
                progressTypes(result));
        assertEquals(List.of(1, 2), state.reviews.stream().map(ResumeOptimizationReviewDO::getIterationNo).toList());
        assertTrue(state.reviews.get(1).getExecutorOutputJson().contains("Second draft"));

        ArgumentCaptor<ResumeOptimizationSuggestionDO> suggestionCaptor =
                ArgumentCaptor.forClass(ResumeOptimizationSuggestionDO.class);
        verify(suggestionMapper).insert(suggestionCaptor.capture());
        assertEquals("Built reliable Spring APIs with latency metrics", suggestionCaptor.getValue().getSuggestedText());
        assertEquals(1, result.getSuggestions().size());
        assertEquals("Built reliable Spring APIs with latency metrics", result.getSuggestions().get(0).getSuggestedText());
    }

    @Test
    void reviewerRiskStopsIterationImmediately() {
        setupCreateTaskMocks(
                List.of(executorOutput()),
                List.of(reviewerOutput(0.93D, true, List.of("Invented a Kubernetes migration"),
                        List.of(), List.of("suggestion-1")))
        );

        CareerOptimizationTaskVO result = newService().createTask(createRequest());

        verify(singleFlightLlmService, times(2)).chat(anyString(), anyString(), anyString(), any(ChatRequest.class));
        assertEquals(CareerTaskStatus.NEEDS_REVIEW.name(), result.getStatus());
        assertEquals(OptimizationReviewStatus.BLOCKED_BY_RISK.name(), result.getReviewStatus());
        assertEquals(List.of("GENERATING", "REVIEWING", "NEEDS_REVIEW"), progressTypes(result));
    }

    @Test
    void reviewerRiskBlocksReviewAndMarksTaskNeedsReview() {
        setupCreateTaskMocks(
                reviewerOutput(0.93D, true, List.of("Invented a Kubernetes migration"), List.of(), List.of("suggestion-1"))
        );

        CareerOptimizationTaskVO result = newService().createTask(createRequest());

        assertEquals(CareerTaskStatus.NEEDS_REVIEW.name(), result.getStatus());
        assertEquals(OptimizationReviewStatus.BLOCKED_BY_RISK.name(), result.getReviewStatus());
        assertEquals(List.of("GENERATING", "REVIEWING", "NEEDS_REVIEW"), progressTypes(result));
        ArgumentCaptor<ResumeOptimizationReviewDO> reviewCaptor =
                ArgumentCaptor.forClass(ResumeOptimizationReviewDO.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        assertTrue(reviewCaptor.getValue().getTruthfulnessRisk());
    }

    private ResumeOptimizationSuggestionDO lowRiskSuggestion() {
        return ResumeOptimizationSuggestionDO.builder()
                .riskLevel("LOW")
                .build();
    }

    private CreateTaskState setupCreateTaskMocks(Map<String, Object> reviewerOutput) {
        return setupCreateTaskMocks(List.of(executorOutput()), List.of(reviewerOutput));
    }

    private CreateTaskState setupCreateTaskMocks(List<Map<String, Object>> executorOutputs,
                                                 List<Map<String, Object>> reviewerOutputs) {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(ResumeVersionDO.builder()
                .id("resume-1")
                .userId("user-1")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .build());
        when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(JobDescriptionDO.builder()
                .id("jd-1")
                .userId("user-1")
                .parsedJson("{\"requiredSkills\":[\"Java\"]}")
                .build());
        when(careerRetrievalEnhancementService.enhanceOptimization(any(ResumeVersionDO.class),
                any(JobDescriptionDO.class), anyString())).thenReturn(defaultEnhancement());
        AtomicInteger executorIndex = new AtomicInteger();
        AtomicInteger reviewerIndex = new AtomicInteger();
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenAnswer(invocation -> {
                    String scene = invocation.getArgument(0);
                    if ("OPTIMIZATION_EXECUTOR".equals(scene)) {
                        return "executor-response-" + executorIndex.incrementAndGet();
                    }
                    return "reviewer-response-" + reviewerIndex.incrementAndGet();
                });
        when(careerJsonParser.parseObject(anyString())).thenAnswer(invocation -> {
            String response = invocation.getArgument(0);
            if (response.startsWith("executor-response-")) {
                int index = Integer.parseInt(response.substring("executor-response-".length())) - 1;
                return executorOutputs.get(Math.min(index, executorOutputs.size() - 1));
            }
            if (response.startsWith("reviewer-response-")) {
                int index = Integer.parseInt(response.substring("reviewer-response-".length())) - 1;
                return reviewerOutputs.get(Math.min(index, reviewerOutputs.size() - 1));
            }
            return Map.of();
        });

        CreateTaskState state = new CreateTaskState();
        doAnswer(invocation -> {
            ResumeOptimizationTaskDO task = invocation.getArgument(0);
            task.setId("task-1");
            state.task.set(task);
            return 1;
        }).when(taskMapper).insert(any(ResumeOptimizationTaskDO.class));
        doAnswer(invocation -> {
            ResumeOptimizationTaskDO task = invocation.getArgument(0);
            state.task.set(task);
            return 1;
        }).when(taskMapper).updateById(any(ResumeOptimizationTaskDO.class));
        doAnswer(invocation -> {
            ResumeOptimizationSuggestionDO suggestion = invocation.getArgument(0);
            suggestion.setId("suggestion-1");
            return 1;
        }).when(suggestionMapper).insert(any(ResumeOptimizationSuggestionDO.class));
        doAnswer(invocation -> {
            ResumeOptimizationReviewDO review = invocation.getArgument(0);
            review.setId("review-1");
            state.reviews.add(review);
            return 1;
        }).when(reviewMapper).insert(any(ResumeOptimizationReviewDO.class));
        AtomicInteger eventIndex = new AtomicInteger();
        doAnswer(invocation -> {
            CareerProgressEventDO event = invocation.getArgument(0);
            event.setId("event-" + eventIndex.incrementAndGet());
            state.events.add(event);
            return 1;
        }).when(progressEventMapper).insert(any(CareerProgressEventDO.class));
        return state;
    }

    private Map<String, Object> executorOutput() {
        return executorOutput("ok", "Built reliable APIs");
    }

    private Map<String, Object> executorOutput(String summary, String suggestedText) {
        return Map.of(
                "summary", summary,
                "suggestions", List.of(Map.of(
                        "category", "impact",
                        "title", "Improve wording",
                        "originalText", "Built APIs",
                        "suggestedText", suggestedText,
                        "reason", "Better signal",
                        "riskLevel", "LOW"
                ))
        );
    }

    private Map<String, Object> reviewerOutput(double qualityScore,
                                               boolean truthfulnessRisk,
                                               Object unsupportedClaims,
                                               List<String> acceptedSuggestionIds,
                                               List<String> rejectedSuggestionIds) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("qualityScore", qualityScore);
        output.put("truthfulnessRisk", truthfulnessRisk);
        output.put("unsupportedClaims", unsupportedClaims);
        output.put("acceptedSuggestionIds", acceptedSuggestionIds);
        output.put("rejectedSuggestionIds", rejectedSuggestionIds);
        output.put("revisionInstructions", List.of("Keep claims grounded in the source resume"));
        output.put("riskSummary", truthfulnessRisk ? "Reviewer found truthfulness risk" : "No truthfulness risk");
        return output;
    }

    private CareerOptimizationCreateRequest createRequest() {
        CareerOptimizationCreateRequest request = new CareerOptimizationCreateRequest();
        request.setResumeVersionId("resume-1");
        request.setJdId("jd-1");
        return request;
    }

    private List<String> progressTypes(CareerOptimizationTaskVO task) {
        return task.getProgressEvents().stream()
                .map(CareerProgressEventVO::getEventType)
                .toList();
    }

    private static final class CreateTaskState {
        private final AtomicReference<ResumeOptimizationTaskDO> task = new AtomicReference<>();
        private final List<ResumeOptimizationReviewDO> reviews = new ArrayList<>();
        private final List<CareerProgressEventDO> events = new ArrayList<>();
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

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeVersionDO> anyResumeVersionWrapper() {
        return (Wrapper<ResumeVersionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<JobDescriptionDO> anyJobDescriptionWrapper() {
        return (Wrapper<JobDescriptionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeOptimizationTaskDO> anyTaskWrapper() {
        return (Wrapper<ResumeOptimizationTaskDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeOptimizationReviewDO> anyReviewWrapper() {
        return (Wrapper<ResumeOptimizationReviewDO>) any(Wrapper.class);
    }
}
