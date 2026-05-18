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
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewAnswerRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewCreateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.service.followup.DefaultInterviewFollowUpDecisionService;
import com.nageoffer.ai.ragent.career.service.impl.InterviewSessionServiceImpl;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionRecoveryService;
import com.nageoffer.ai.ragent.career.service.runtime.InterviewTurnRuntimeServiceImpl;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSessionStateTest {

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private ResumeVersionMapper resumeVersionMapper;

    @Mock
    private JobDescriptionMapper jobDescriptionMapper;

    @Mock
    private CareerJsonParser careerJsonParser;

    @Mock
    private CareerSingleFlightLlmService singleFlightLlmService;

    @Mock
    private InterviewSessionRecoveryService interviewSessionRecoveryService;

    @Mock
    private CareerRetrievalEnhancementService careerRetrievalEnhancementService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final List<InterviewSessionDO> sessions = new ArrayList<>();
    private final List<InterviewTurnDO> turns = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewSessionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewTurnDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobDescriptionDO.class);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createSessionPersistsFirstQuestionFromPlan() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"questions\":[]}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(plan());

        CareerInterviewSessionVO result = newService().createSession(createRequest());

        assertEquals("CREATED", result.getStatus());
        assertEquals(1, result.getCurrentTurnNo());
        assertNotNull(result.getCurrentQuestion());
        assertEquals("Explain your most complex Spring Boot project.", result.getCurrentQuestion().getQuestion());
        assertEquals("PROJECT_DEEP_DIVE", result.getCurrentQuestion().getTurnType());
        assertNotNull(result.getPlan());
        assertEquals(1, sessions.size());
        assertEquals("user-1", sessions.get(0).getCreatedBy());
        assertEquals("CREATED", sessions.get(0).getStatus());
        assertEquals(1, turns.size());
        assertEquals("ASKED", turns.get(0).getStatus());
        assertEquals("WAITING_ANSWER", turns.get(0).getAnswerStatus());
        assertEquals("NOT_STARTED", turns.get(0).getEvaluationStatus());
        assertEquals("NONE", turns.get(0).getCompensationStatus());
        assertEquals("PROJECT_DEEP_DIVE", turns.get(0).getTurnType());
        assertEquals("Explain your most complex Spring Boot project.", turns.get(0).getQuestion());
    }

    @Test
    void submitAnswerEvaluatesCurrentTurnAndCreatesFollowUpWhenRequired() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"score\":88}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(evaluation(true, "Which tradeoff mattered most?"));
        ArgumentCaptor<ChatRequest> chatRequestCaptor = ArgumentCaptor.forClass(ChatRequest.class);

        CareerInterviewTurnVO result = newService().submitAnswer("session-1", answer("I designed the module boundaries."));

        assertEquals("EVALUATED", result.getStatus());
        assertEquals("ANSWER_SAVED", result.getAnswerStatus());
        assertEquals("EVALUATED", result.getEvaluationStatus());
        assertEquals("FOLLOW_UP_CREATED", result.getFollowUpDecisionStatus());
        assertEquals(1, result.getAttemptCount());
        assertEquals(88, result.getScore());
        assertEquals("I designed the module boundaries.", result.getAnswer());
        assertNotNull(result.getFeedback());
        assertEquals(2, turns.size());
        assertEquals("FOLLOW_UP", turns.get(1).getTurnType());
        assertEquals("Which tradeoff mattered most?", turns.get(1).getQuestion());
        assertEquals("ASKED", turns.get(1).getStatus());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
        assertEquals("RUNNING", sessions.get(0).getStatus());
        verify(singleFlightLlmService).chat(anyString(), anyString(), any(), chatRequestCaptor.capture());
        String prompt = chatRequestCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("{\"skills\":[\"Java\"]}"));
        assertTrue(prompt.contains("{\"requiredSkills\":[\"Java\"]}"));
    }

    @Test
    void duplicateAnswerSubmissionReplaysExistingEvaluationWithoutCreatingFollowUp() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"score\":88}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(evaluation(true, "Which tradeoff mattered most?"));

        CareerInterviewAnswerRequest firstRequest = answer("I designed the module boundaries.");
        firstRequest.setAnswerRevision("rev-1");
        CareerInterviewTurnVO first = newService().submitAnswer("session-1", firstRequest);
        sessions.get(0).setCurrentTurnNo(1);
        CareerInterviewAnswerRequest duplicateRequest = answer("I designed the module boundaries.");
        duplicateRequest.setAnswerRevision("rev-1");

        CareerInterviewTurnVO duplicate = newService().submitAnswer("session-1", duplicateRequest);

        assertEquals(first.getId(), duplicate.getId());
        assertEquals("EVALUATED", duplicate.getStatus());
        assertEquals("FOLLOW_UP_CREATED", duplicate.getFollowUpDecisionStatus());
        assertEquals(2, turns.size());
        verify(singleFlightLlmService).chat(anyString(), anyString(), any(), any(ChatRequest.class));
    }

    @Test
    void submitAnswerWithoutFollowUpCreatesNextPlannedQuestionThenCompletesAtEnd() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"score\":80}");
        when(careerJsonParser.parseObject(anyString()))
                .thenReturn(evaluation(false, null))
                .thenReturn(evaluation(false, null));

        CareerInterviewTurnVO first = newService().submitAnswer("session-1", answer("Project answer"));

        assertEquals("EVALUATED", first.getStatus());
        assertEquals(2, turns.size());
        assertEquals("TECHNICAL", turns.get(1).getTurnType());
        assertEquals("How do you tune slow SQL queries?", turns.get(1).getQuestion());
        assertEquals("RUNNING", sessions.get(0).getStatus());

        CareerInterviewTurnVO second = newService().submitAnswer("session-1", answer("I inspect plans and indexes."));

        assertEquals("EVALUATED", second.getStatus());
        assertEquals(2, turns.size());
        assertEquals("COMPLETED", sessions.get(0).getStatus());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
    }

    /**
     * 验证面试评分低且模型未给追问问题时，提交答案仍会通过规则链创建兜底追问。
     */
    @Test
    void submitAnswerCreatesFallbackFollowUpWhenScoreIsLowWithoutLlmQuestion() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"score\":55}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(lowScoreEvaluation());

        CareerInterviewTurnVO result = newService().submitAnswer("session-1", answer("I missed some details."));

        assertEquals("EVALUATED", result.getStatus());
        assertEquals("FOLLOW_UP_CREATED", result.getFollowUpDecisionStatus());
        assertEquals(2, turns.size());
        assertEquals("FOLLOW_UP", turns.get(1).getTurnType());
        assertEquals("能否再补充一个关键细节，说明你的思路或取舍？", turns.get(1).getQuestion());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
    }

    @Test
    void pausedSessionReturnsCurrentQuestionAndResumesToRunning() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        sessions.get(0).setStatus("PAUSED");

        CareerInterviewTurnVO result = newService().nextQuestion("session-1");

        assertEquals("Explain your most complex Spring Boot project.", result.getQuestion());
        assertEquals("ASKED", result.getStatus());
        assertEquals("RUNNING", sessions.get(0).getStatus());
    }

    @Test
    void nextQuestionRejectsInvalidPersistedSessionStatusAsClientException() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        sessions.get(0).setStatus("BROKEN_STATUS");

        assertThrows(ClientException.class, () -> newService().nextQuestion("session-1"));
    }

    @Test
    void completedSessionRejectsAnswer() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        sessions.get(0).setStatus("COMPLETED");

        assertThrows(ClientException.class,
                () -> newService().submitAnswer("session-1", answer("Too late")));

        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), any(), any(ChatRequest.class));
    }

    @Test
    void scoringFailureKeepsAnswerAndCurrentQuestionAvailableForRetry() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenThrow(new RuntimeException("llm down"));

        CareerInterviewTurnVO result = newService().submitAnswer("session-1",
                answer("I designed the module boundaries."));

        ArgumentCaptor<InterviewTurnDO> turnCaptor = ArgumentCaptor.forClass(InterviewTurnDO.class);
        verify(turnMapper, atLeastOnce()).updateById(turnCaptor.capture());
        InterviewTurnDO failedTurn = turnCaptor.getAllValues().get(turnCaptor.getAllValues().size() - 1);
        assertEquals("WAITING_RETRY", failedTurn.getStatus());
        assertEquals("EVALUATION_FAILED", failedTurn.getEvaluationStatus());
        assertEquals("COMPENSATING", failedTurn.getCompensationStatus());
        assertNotNull(failedTurn.getLastError());
        assertEquals("I designed the module boundaries.", failedTurn.getAnswer());
        assertEquals("WAITING_RETRY", result.getStatus());
        assertEquals("I designed the module boundaries.", result.getAnswer());
        assertEquals(1, result.getTurnNo());
        assertEquals(1, turns.size());
        assertEquals("RUNNING", sessions.get(0).getStatus());
        assertEquals("I designed the module boundaries.", newService().querySession("session-1").getCurrentQuestion().getAnswer());
        assertEquals("WAITING_RETRY", newService().querySession("session-1").getCurrentQuestion().getStatus());
    }

    @Test
    void retryEvaluationReusesSavedAnswerAndAdvancesToNextTurn() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenThrow(new RuntimeException("llm down"))
                .thenReturn("{\"score\":88}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(evaluation(false, null));

        CareerInterviewTurnVO failed = newService().submitAnswer("session-1",
                answer("I designed the module boundaries."));
        CareerInterviewTurnVO retried = newService().retryEvaluation("session-1", 1);

        assertEquals("WAITING_RETRY", failed.getStatus());
        assertEquals("EVALUATED", retried.getStatus());
        assertEquals("I designed the module boundaries.", retried.getAnswer());
        assertEquals(88, retried.getScore());
        assertEquals(2, retried.getAttemptCount());
        assertEquals("EVALUATED", retried.getEvaluationStatus());
        assertEquals("COMPENSATED", retried.getCompensationStatus());
        assertNull(retried.getLastError());
        assertEquals(2, turns.size());
        assertEquals("TECHNICAL", turns.get(1).getTurnType());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
        assertEquals("RUNNING", sessions.get(0).getStatus());
    }

    @Test
    void retryEvaluationRejectsNonFailedTurn() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();

        assertThrows(ClientException.class, () -> newService().retryEvaluation("session-1", 1));

        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), any(), any(ChatRequest.class));
    }

    @Test
    void retryEvaluationRejectsClaimedTurnWithoutCallingModel() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithFailedTurn();
        doAnswer(invocation -> 0).when(turnMapper).update(isNull(), anyTurnWrapper());

        assertThrows(ClientException.class, () -> newService().retryEvaluation("session-1", 1));

        assertEquals(1, turns.size());
        assertEquals("WAITING_RETRY", turns.get(0).getStatus());
        assertEquals("EVALUATION_FAILED", turns.get(0).getEvaluationStatus());
        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), any(), any(ChatRequest.class));
    }

    @Test
    void retryEvaluationKeepsWaitingRetryWhenSecondAttemptFails() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenThrow(new RuntimeException("llm down"))
                .thenThrow(new RuntimeException("still down"));

        newService().submitAnswer("session-1", answer("I designed the module boundaries."));
        CareerInterviewTurnVO retried = newService().retryEvaluation("session-1", 1);

        assertEquals("WAITING_RETRY", retried.getStatus());
        assertEquals("I designed the module boundaries.", retried.getAnswer());
        assertEquals("EVALUATION_FAILED", retried.getEvaluationStatus());
        assertEquals("COMPENSATING", retried.getCompensationStatus());
        assertEquals(2, retried.getAttemptCount());
        assertNotNull(retried.getLastError());
        assertEquals(1, turns.size());
        assertEquals(1, sessions.get(0).getCurrentTurnNo());
    }

    @Test
    void compensationWorkerRetriesPendingTurnsWithoutUserContext() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenThrow(new RuntimeException("llm down"))
                .thenReturn("{\"score\":88}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(evaluation(false, null));

        newService().submitAnswer("session-1", answer("I designed the module boundaries."));
        UserContext.clear();
        int compensated = newService().compensatePendingEvaluations(10);

        assertEquals(1, compensated);
        assertEquals("EVALUATED", turns.get(0).getStatus());
        assertEquals(2, turns.get(0).getAttemptCount());
        assertEquals("COMPENSATED", turns.get(0).getCompensationStatus());
        assertEquals(2, turns.size());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
    }

    @Test
    void compensationWorkerSkipsTurnWhenRetryClaimAlreadyTaken() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithFailedTurn();
        doAnswer(invocation -> 0).when(turnMapper).update(isNull(), anyTurnWrapper());
        UserContext.clear();

        int compensated = newService().compensatePendingEvaluations(10);

        assertEquals(0, compensated);
        assertEquals(1, turns.size());
        assertEquals("WAITING_RETRY", turns.get(0).getStatus());
        assertEquals("EVALUATION_FAILED", turns.get(0).getEvaluationStatus());
        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), any(), any(ChatRequest.class));
    }

    @Test
    void linkedResumeAndJobMustBeVisibleToCurrentUser() {
        login();
        stubVisibleLinkedObjects(false, true);

        assertThrows(ClientException.class, () -> newService().createSession(createRequest()));

        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(resumeVersionMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("deleted"));
        verify(jobDescriptionMapper, never()).selectOne(anyJobDescriptionWrapper());
        verify(sessionMapper, never()).insert(any(InterviewSessionDO.class));
    }

    @Test
    void pauseRejectsTerminalSessionsAndFinishCompletesActiveSession() {
        login();
        stubVisibleLinkedObjects(true, true);
        stubPersistence();
        seedSessionWithAskedTurn();

        newService().finish("session-1");
        assertEquals(InterviewSessionStatus.COMPLETED.name(), sessions.get(0).getStatus());
        assertThrows(ClientException.class, () -> newService().pause("session-1"));
    }

    private InterviewSessionServiceImpl newService() {
        return new InterviewSessionServiceImpl(
                sessionMapper,
                turnMapper,
                resumeVersionMapper,
                jobDescriptionMapper,
                careerJsonParser,
                singleFlightLlmService,
                new InterviewTurnRuntimeServiceImpl(),
                interviewSessionRecoveryService,
                careerRetrievalEnhancementService,
                new DefaultInterviewFollowUpDecisionService(),
                transactionManager
        );
    }

    private void stubRetrievalEnhancement() {
        lenient().when(careerRetrievalEnhancementService.enhanceInterview(
                any(ResumeVersionDO.class), any(JobDescriptionDO.class), anyString()))
                .thenReturn(defaultEnhancement());
    }

    private CareerRetrievalEnhancement defaultEnhancement() {
        return new CareerRetrievalEnhancement(CareerRetrievalScenario.INTERVIEW, "query-only interview evidence",
                List.of(CareerRetrievalEvidence.builder()
                        .type(CareerRetrievalEvidenceType.HYDE_QUERY)
                        .sourceId("test")
                        .text("query-only interview evidence")
                        .score(1F)
                        .queryOnly(true)
                        .build()));
    }

    private void login() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
    }

    private CareerInterviewCreateRequest createRequest() {
        CareerInterviewCreateRequest request = new CareerInterviewCreateRequest();
        request.setResumeVersionId("resume-1");
        request.setJdId("jd-1");
        return request;
    }

    private CareerInterviewAnswerRequest answer(String text) {
        CareerInterviewAnswerRequest request = new CareerInterviewAnswerRequest();
        request.setAnswer(text);
        return request;
    }

    private void stubVisibleLinkedObjects(boolean resumeVisible, boolean jobVisible) {
        lenient().when(resumeVersionMapper.selectOne(anyResumeVersionWrapper()))
                .thenReturn(resumeVisible ? resumeVersion() : null);
        if (resumeVisible) {
            lenient().when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper()))
                    .thenReturn(jobVisible ? jobDescription() : null);
        }
    }

    private void stubPersistence() {
        lenient().doAnswer(invocation -> {
            InterviewSessionDO session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId("session-" + ids.getAndIncrement());
            }
            sessions.add(session);
            return 1;
        }).when(sessionMapper).insert(any(InterviewSessionDO.class));
        lenient().doAnswer(invocation -> {
            InterviewTurnDO turn = invocation.getArgument(0);
            if (turn.getId() == null) {
                turn.setId("turn-" + ids.getAndIncrement());
            }
            turns.add(turn);
            return 1;
        }).when(turnMapper).insert(any(InterviewTurnDO.class));
        lenient().doAnswer(invocation -> 1).when(sessionMapper).updateById(any(InterviewSessionDO.class));
        lenient().doAnswer(invocation -> 1).when(turnMapper).updateById(any(InterviewTurnDO.class));
        lenient().doAnswer(invocation -> claimRetryableTurn()).when(turnMapper).update(isNull(), anyTurnWrapper());
        lenient().when(sessionMapper.selectOne(anySessionWrapper()))
                .thenAnswer(invocation -> selectSession(invocation.getArgument(0)));
        lenient().when(turnMapper.selectOne(anyTurnWrapper()))
                .thenAnswer(invocation -> selectTurn(invocation.getArgument(0)));
        lenient().when(turnMapper.selectList(anyTurnWrapper())).thenAnswer(invocation -> List.copyOf(turns));
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().doAnswer(invocation -> null).when(transactionManager).commit(any(TransactionStatus.class));
        lenient().doAnswer(invocation -> null).when(transactionManager).rollback(any(TransactionStatus.class));
    }

    private int claimRetryableTurn() {
        return turns.stream()
                .filter(this::retryableTurn)
                .findFirst()
                .map(turn -> {
                    turn.setEvaluationStatus("EVALUATING");
                    turn.setCompensationStatus("COMPENSATING");
                    turn.setAttemptCount((turn.getAttemptCount() == null ? 0 : turn.getAttemptCount()) + 1);
                    turn.setLastError(null);
                    return 1;
                })
                .orElse(0);
    }

    private boolean retryableTurn(InterviewTurnDO turn) {
        return "WAITING_RETRY".equals(turn.getStatus())
                && "EVALUATION_FAILED".equals(turn.getEvaluationStatus())
                && "COMPENSATING".equals(turn.getCompensationStatus())
                && turn.getAnswer() != null;
    }

    private void seedSessionWithAskedTurn() {
        InterviewSessionDO session = InterviewSessionDO.builder()
                .id("session-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status("CREATED")
                .planJson("{\"questions\":[{\"type\":\"PROJECT_DEEP_DIVE\",\"question\":\"Explain your most complex Spring Boot project.\"},{\"type\":\"TECHNICAL\",\"question\":\"How do you tune slow SQL queries?\"}]}")
                .currentTurnNo(1)
                .createdBy("user-1")
                .updatedBy("user-1")
                .build();
        sessions.add(session);
        turns.add(InterviewTurnDO.builder()
                .id("turn-1")
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(1)
                .turnType("PROJECT_DEEP_DIVE")
                .question("Explain your most complex Spring Boot project.")
                .status("ASKED")
                .answerStatus("WAITING_ANSWER")
                .evaluationStatus("NOT_STARTED")
                .followUpDecisionStatus("NOT_STARTED")
                .compensationStatus("NONE")
                .attemptCount(0)
                .build());
    }

    private void seedSessionWithFailedTurn() {
        seedSessionWithAskedTurn();
        InterviewTurnDO turn = turns.get(0);
        turn.setStatus("WAITING_RETRY");
        turn.setAnswerStatus("ANSWER_SAVED");
        turn.setEvaluationStatus("EVALUATION_FAILED");
        turn.setFollowUpDecisionStatus("NOT_STARTED");
        turn.setCompensationStatus("COMPENSATING");
        turn.setAnswer("I designed the module boundaries.");
        turn.setStepIdempotencyKey("session-1:1:rev-1");
        turn.setAttemptCount(1);
        turn.setLastError("RuntimeException: llm down");
        sessions.get(0).setStatus("RUNNING");
    }

    private InterviewSessionDO selectSession(Wrapper<InterviewSessionDO> wrapper) {
        List<Object> values = wrapperValues(wrapper);
        if (sessions.size() == 1) {
            return sessions.get(0);
        }
        return sessions.stream()
                .filter(session -> values.contains(session.getId()))
                .filter(session -> values.contains(session.getUserId()))
                .findFirst()
                .orElse(null);
    }

    private InterviewTurnDO selectTurn(Wrapper<InterviewTurnDO> wrapper) {
        List<Object> values = wrapperValues(wrapper);
        if (sessions.size() == 1) {
            Integer currentTurnNo = sessions.get(0).getCurrentTurnNo();
            return turns.stream()
                    .filter(turn -> currentTurnNo.equals(turn.getTurnNo()))
                    .findFirst()
                    .orElse(null);
        }
        return turns.stream()
                .filter(turn -> values.contains(turn.getSessionId()))
                .filter(turn -> values.contains(turn.getUserId()))
                .filter(turn -> values.contains(turn.getTurnNo()))
                .findFirst()
                .orElse(null);
    }

    private List<Object> wrapperValues(Wrapper<?> wrapper) {
        if (wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper) {
            return new ArrayList<>(abstractWrapper.getParamNameValuePairs().values());
        }
        return List.of();
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

    private Map<String, Object> plan() {
        return Map.of("questions", List.of(
                Map.of("type", "PROJECT_DEEP_DIVE", "question", "Explain your most complex Spring Boot project."),
                Map.of("type", "TECHNICAL", "question", "How do you tune slow SQL queries?")
        ));
    }

    private Map<String, Object> evaluation(boolean followUpRequired, String followUpQuestion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", 88);
        result.put("feedback", Map.of("summary", "Clear and concrete"));
        result.put("strengths", List.of("structured"));
        result.put("weaknesses", List.of());
        result.put("followUpRequired", followUpRequired);
        result.put("followUpQuestion", followUpQuestion);
        return result;
    }

    // 构造低分且没有 LLM 追问问题的评分结果，验证规则链兜底追问。
    private Map<String, Object> lowScoreEvaluation() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", 55);
        result.put("feedback", "Needs more detail");
        result.put("strengths", List.of());
        result.put("weaknesses", List.of());
        result.put("followUpRequired", false);
        result.put("followUpQuestion", null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewSessionDO> anySessionWrapper() {
        return (Wrapper<InterviewSessionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewTurnDO> anyTurnWrapper() {
        return (Wrapper<InterviewTurnDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeVersionDO> anyResumeVersionWrapper() {
        return (Wrapper<ResumeVersionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<JobDescriptionDO> anyJobDescriptionWrapper() {
        return (Wrapper<JobDescriptionDO>) any(Wrapper.class);
    }
}
