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
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewAnswerRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewTurnIdempotencyTest {

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
    void sameTurnRevisionReplaysExistingEvaluationAfterSessionMovedForward() {
        login();
        stubVisibleLinkedObjects();
        stubPersistence();
        seedRunningSession();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"score\":88}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(evaluation(true));

        CareerInterviewAnswerRequest firstRequest = answer(1, "rev-1", "I split the runtime state cleanly.");
        CareerInterviewTurnVO first = newService().submitAnswer("session-1", firstRequest);

        assertEquals("EVALUATED", first.getStatus());
        assertEquals("FOLLOW_UP_CREATED", first.getFollowUpDecisionStatus());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
        assertEquals(2, turns.size());

        CareerInterviewTurnVO replay = newService().submitAnswer("session-1", firstRequest);

        assertEquals(first.getId(), replay.getId());
        assertEquals("EVALUATED", replay.getStatus());
        assertEquals("FOLLOW_UP_CREATED", replay.getFollowUpDecisionStatus());
        assertEquals(first.getStepIdempotencyKey(), replay.getStepIdempotencyKey());
        assertEquals(2, turns.size());
        verify(singleFlightLlmService).chat(anyString(), anyString(), any(), any(ChatRequest.class));
        verifyNoMoreInteractions(singleFlightLlmService);
    }

    @Test
    void staleTurnWithDifferentRevisionIsRejectedAfterSessionMovedForward() {
        login();
        stubVisibleLinkedObjects();
        stubPersistence();
        seedRunningSession();
        stubRetrievalEnhancement();
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("{\"score\":88}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(evaluation(true));

        newService().submitAnswer("session-1", answer(1, "rev-1", "First answer."));

        CareerInterviewAnswerRequest staleRequest = answer(1, "rev-2", "Changed stale answer.");
        assertThrows(ClientException.class, () -> newService().submitAnswer("session-1", staleRequest));
        assertEquals(2, turns.size());
        verify(singleFlightLlmService).chat(anyString(), anyString(), any(), any(ChatRequest.class));
        verifyNoMoreInteractions(singleFlightLlmService);
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

    private CareerInterviewAnswerRequest answer(Integer turnNo, String revision, String text) {
        CareerInterviewAnswerRequest request = new CareerInterviewAnswerRequest();
        request.setTurnNo(turnNo);
        request.setAnswerRevision(revision);
        request.setAnswer(text);
        return request;
    }

    private void stubVisibleLinkedObjects() {
        lenient().when(resumeVersionMapper.selectOne(anyResumeVersionWrapper())).thenReturn(ResumeVersionDO.builder()
                .id("resume-1")
                .userId("user-1")
                .contentJson("{\"skills\":[\"Java\"]}")
                .build());
        lenient().when(jobDescriptionMapper.selectOne(anyJobDescriptionWrapper())).thenReturn(JobDescriptionDO.builder()
                .id("jd-1")
                .userId("user-1")
                .parsedJson("{\"requiredSkills\":[\"Java\"]}")
                .build());
    }

    private void stubPersistence() {
        lenient().doAnswer(invocation -> {
            InterviewTurnDO turn = invocation.getArgument(0);
            if (turn.getId() == null) {
                turn.setId("turn-" + (turns.size() + 1));
            }
            turns.add(turn);
            return 1;
        }).when(turnMapper).insert(any(InterviewTurnDO.class));
        lenient().doAnswer(invocation -> 1).when(sessionMapper).updateById(any(InterviewSessionDO.class));
        lenient().doAnswer(invocation -> 1).when(turnMapper).updateById(any(InterviewTurnDO.class));
        lenient().doAnswer(invocation -> 1).when(turnMapper).update(isNull(), anyTurnWrapper());
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

    private void seedRunningSession() {
        sessions.add(InterviewSessionDO.builder()
                .id("session-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status("RUNNING")
                .planJson("{\"questions\":[{\"type\":\"PROJECT_DEEP_DIVE\",\"question\":\"Explain your runtime design.\"}]}")
                .currentTurnNo(1)
                .build());
        turns.add(InterviewTurnDO.builder()
                .id("turn-1")
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(1)
                .turnType("PROJECT_DEEP_DIVE")
                .question("Explain your runtime design.")
                .status("ASKED")
                .answerStatus("WAITING_ANSWER")
                .evaluationStatus("NOT_STARTED")
                .followUpDecisionStatus("NOT_STARTED")
                .compensationStatus("NONE")
                .attemptCount(0)
                .build());
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
            return turns.stream()
                    .filter(turn -> Integer.valueOf(1).equals(turn.getTurnNo()))
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

    private Map<String, Object> evaluation(boolean followUpRequired) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", 88);
        result.put("feedback", "Clear");
        result.put("strengths", List.of("structured"));
        result.put("weaknesses", List.of());
        result.put("followUpRequired", followUpRequired);
        result.put("followUpQuestion", "What failed first?");
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
