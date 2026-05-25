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

import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.service.interview.InterviewPlanExecuteReflectService;
import com.nageoffer.ai.ragent.career.service.interview.agent.InterviewCoordinatorAgent;
import com.nageoffer.ai.ragent.career.service.interview.agent.InterviewReflectorAgent;
import com.nageoffer.ai.ragent.career.service.interview.agent.JdAlignmentInterviewAgent;
import com.nageoffer.ai.ragent.career.service.interview.agent.TechnicalInterviewerAgent;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalScenario;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewPlanExecuteReflectTest {

    @Mock
    private CareerSingleFlightLlmService singleFlightLlmService;

    @Mock
    private CareerJsonParser careerJsonParser;

    @Test
    void createInitialPlanRunsJdAlignmentCoordinatorAndTechnicalQuestionAgents() {
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("alignment-json")
                .thenReturn("plan-json")
                .thenReturn("question-json");
        when(careerJsonParser.parseObject("alignment-json")).thenReturn(mapOf("summary", "JD 重点是高并发和缓存"));
        when(careerJsonParser.parseObject("plan-json")).thenReturn(plan());
        when(careerJsonParser.parseObject("question-json")).thenReturn(question("TECHNICAL", "请解释线程池拒绝策略。"));
        ArgumentCaptor<String> sceneCaptor = ArgumentCaptor.forClass(String.class);

        InterviewPlanExecuteReflectService.InitialPlanResult result =
                newService().createInitialPlan(resumeVersion(), job(), enhancement());

        assertEquals("请解释线程池拒绝策略。", result.firstQuestion().get("question"));
        assertEquals("JD 重点是高并发和缓存", result.plan().get("jdAlignmentSummary"));
        assertTrue(result.plan().containsKey("agentWorkflow"));
        verify(singleFlightLlmService, times(3)).chat(sceneCaptor.capture(), anyString(), any(), any(ChatRequest.class));
        assertEquals(List.of("INTERVIEW_JD_ALIGNMENT", "INTERVIEW_COORDINATOR", "INTERVIEW_TECHNICAL"),
                sceneCaptor.getAllValues());
    }

    @Test
    void reflectAfterEvaluationReturnsProbeDecisionForExistingRuleChain() {
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("reflection-json");
        when(careerJsonParser.parseObject("reflection-json")).thenReturn(mapOf(
                "decision", "PROBE",
                "reason", "候选人没有解释降级策略",
                "followUpQuestion", "如果 Redis 抖动，你会如何降级？"));

        InterviewPlanExecuteReflectService.ReflectionResult result = newService().reflectAfterEvaluation(
                session(),
                turn(),
                new InterviewPlanExecuteReflectService.EvaluationSnapshot(55, mapOf("summary", "回答偏浅"),
                        false, null),
                List.of(turn()));

        assertEquals("PROBE", result.decision());
        assertEquals("如果 Redis 抖动，你会如何降级？", result.followUpQuestion());
        assertEquals(Boolean.TRUE, result.followUpRequired());
        assertTrue(result.auditPayload().containsKey("reflectorDecision"));
        verify(singleFlightLlmService).chat(anyString(), anyString(), any(), any(ChatRequest.class));
    }

    private InterviewPlanExecuteReflectService newService() {
        return new InterviewPlanExecuteReflectService(
                new JdAlignmentInterviewAgent(singleFlightLlmService, careerJsonParser),
                new InterviewCoordinatorAgent(singleFlightLlmService, careerJsonParser),
                new TechnicalInterviewerAgent(singleFlightLlmService, careerJsonParser),
                new InterviewReflectorAgent(singleFlightLlmService, careerJsonParser)
        );
    }

    private ResumeVersionDO resumeVersion() {
        return ResumeVersionDO.builder()
                .id("resume-1")
                .userId("user-1")
                .contentJson("{\"skills\":[\"Java\",\"Redis\"]}")
                .build();
    }

    private JobDescriptionDO job() {
        return JobDescriptionDO.builder()
                .id("job-1")
                .userId("user-1")
                .parsedJson("{\"requiredSkills\":[\"Java\",\"Redis\"]}")
                .build();
    }

    private InterviewSessionDO session() {
        return InterviewSessionDO.builder()
                .id("session-1")
                .userId("user-1")
                .traceId("trace-session-1")
                .planJson("{\"questions\":[]}")
                .build();
    }

    private InterviewTurnDO turn() {
        return InterviewTurnDO.builder()
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(1)
                .question("如何设计缓存降级？")
                .answer("我会加缓存。")
                .build();
    }

    private CareerRetrievalEnhancement enhancement() {
        return new CareerRetrievalEnhancement(CareerRetrievalScenario.INTERVIEW,
                "interview query", List.of());
    }

    private Map<String, Object> plan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("dimensions", List.of("TECHNICAL"));
        plan.put("questions", List.of(question("PROJECT_DEEP_DIVE", "讲讲你最复杂的项目。")));
        return plan;
    }

    private Map<String, Object> question(String type, String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("question", question);
        result.put("expectedSignals", List.of("架构取舍", "异常处理"));
        result.put("difficulty", "MEDIUM");
        return result;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}
