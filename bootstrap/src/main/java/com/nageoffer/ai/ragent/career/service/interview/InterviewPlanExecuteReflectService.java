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

package com.nageoffer.ai.ragent.career.service.interview;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.service.interview.agent.InterviewCoordinatorAgent;
import com.nageoffer.ai.ragent.career.service.interview.agent.InterviewReflectorAgent;
import com.nageoffer.ai.ragent.career.service.interview.agent.JdAlignmentInterviewAgent;
import com.nageoffer.ai.ragent.career.service.interview.agent.TechnicalInterviewerAgent;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewPlanExecuteReflectService {

    private static final String DECISION_PROBE = "PROBE";
    private static final String DECISION_NEXT = "NEXT";
    private static final String DECISION_STAGE_FINISH = "STAGE_FINISH";
    private static final String DECISION_FINISH = "FINISH";

    private final JdAlignmentInterviewAgent jdAlignmentInterviewAgent;

    private final InterviewCoordinatorAgent interviewCoordinatorAgent;

    private final TechnicalInterviewerAgent technicalInterviewerAgent;

    private final InterviewReflectorAgent interviewReflectorAgent;

    /**
     * 执行 JD 对齐、协调计划和首题生成，形成初始面试计划。
     */
    public InitialPlanResult createInitialPlan(ResumeVersionDO resumeVersion,
                                               JobDescriptionDO job,
                                               CareerRetrievalEnhancement enhancement) {
        Map<String, Object> jdAlignment = jdAlignmentInterviewAgent.align(resumeVersion, job, enhancement);
        Map<String, Object> coordinatorPlan =
                interviewCoordinatorAgent.plan(resumeVersion, job, jdAlignment, enhancement);
        List<Map<String, Object>> questions = readQuestions(coordinatorPlan);
        Map<String, Object> plannedQuestion = questions.isEmpty() ? new LinkedHashMap<>() : questions.get(0);
        Map<String, Object> firstQuestion = technicalInterviewerAgent.selectQuestion(
                resumeVersion.getUserId(),
                resumeVersion.getId() + ":" + job.getId(),
                null,
                coordinatorPlan,
                plannedQuestion,
                1,
                List.of());
        Map<String, Object> plan = enrichPlan(coordinatorPlan, jdAlignment, firstQuestion, 0);
        return new InitialPlanResult(plan, firstQuestion);
    }

    /**
     * 由技术面试官 Agent 生成或选择下一道主问题。
     */
    public Map<String, Object> selectNextMainQuestion(InterviewSessionDO session,
                                                      Map<String, Object> plan,
                                                      List<InterviewTurnDO> sessionTurns,
                                                      Map<String, Object> plannedQuestion,
                                                      int questionIndex) {
        return technicalInterviewerAgent.selectQuestion(
                session.getUserId(),
                session.getId(),
                session.getTraceId(),
                plan,
                plannedQuestion,
                questionIndex,
                sessionTurns == null ? List.of() : sessionTurns);
    }

    /**
     * 反思评分结果并输出流程推进建议，供现有追问规则链和状态机消费。
     */
    public ReflectionResult reflectAfterEvaluation(InterviewSessionDO session,
                                                   InterviewTurnDO turn,
                                                   EvaluationSnapshot evaluation,
                                                   List<InterviewTurnDO> sessionTurns) {
        Map<String, Object> evaluationPayload = evaluationPayload(evaluation);
        Map<String, Object> parsed = interviewReflectorAgent.reflect(session, turn, evaluationPayload,
                sessionTurns == null ? List.of() : sessionTurns);
        String decision = normalizeDecision(parsed.get("decision"), evaluation);
        String reason = text(parsed.get("reason"));
        String followUpQuestion = firstNonBlank(text(parsed.get("followUpQuestion")), evaluation.followUpQuestion());
        boolean followUpRequired = DECISION_PROBE.equals(decision)
                || (Boolean.TRUE.equals(evaluation.followUpRequired()) && StrUtil.isNotBlank(followUpQuestion));
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reflectorDecision", decision);
        audit.put("reason", reason);
        audit.put("followUpQuestion", followUpQuestion);
        audit.put("score", evaluation.score());
        return new ReflectionResult(decision, reason, followUpRequired, followUpQuestion, audit);
    }

    /**
     * 判断反思裁决是否要求直接结束会话。
     */
    public boolean shouldFinish(ReflectionResult reflection) {
        return reflection != null && DECISION_FINISH.equals(reflection.decision());
    }

    /**
     * 将协调者计划补充为可审计的 Plan-and-Execute 计划。
     */
    private Map<String, Object> enrichPlan(Map<String, Object> coordinatorPlan,
                                           Map<String, Object> jdAlignment,
                                           Map<String, Object> firstQuestion,
                                           int firstIndex) {
        Map<String, Object> plan = coordinatorPlan == null ? new LinkedHashMap<>() : new LinkedHashMap<>(coordinatorPlan);
        plan.put("jdAlignment", jdAlignment);
        plan.put("jdAlignmentSummary", firstNonBlank(text(jdAlignment == null ? null : jdAlignment.get("summary")), ""));
        plan.put("agentWorkflow", List.of("JD_ALIGNMENT", "COORDINATOR", "TECHNICAL_INTERVIEWER", "REFLECTOR"));
        List<Map<String, Object>> questions = new ArrayList<>(readQuestions(plan));
        if (questions.isEmpty()) {
            questions.add(firstQuestion);
        } else if (firstQuestion != null && StrUtil.isNotBlank(text(firstQuestion.get("question")))) {
            questions.set(firstIndex, firstQuestion);
        }
        plan.put("questions", questions);
        return plan;
    }

    /**
     * 从计划中读取问题列表。
     */
    private List<Map<String, Object>> readQuestions(Map<String, Object> plan) {
        Object questions = plan == null ? null : plan.get("questions");
        if (!(questions instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                result.add(new LinkedHashMap<>(cast));
            }
        }
        return result;
    }

    /**
     * 构建反思 Agent 使用的评分快照。
     */
    private Map<String, Object> evaluationPayload(EvaluationSnapshot evaluation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("score", evaluation.score());
        payload.put("feedback", evaluation.feedback());
        payload.put("followUpRequired", evaluation.followUpRequired());
        payload.put("followUpQuestion", evaluation.followUpQuestion());
        return payload;
    }

    /**
     * 标准化反思裁决，缺省时沿用评分结果。
     */
    private String normalizeDecision(Object value, EvaluationSnapshot evaluation) {
        String decision = text(value);
        if (decision == null) {
            return Boolean.TRUE.equals(evaluation.followUpRequired()) ? DECISION_PROBE : DECISION_NEXT;
        }
        String normalized = decision.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case DECISION_PROBE, DECISION_NEXT, DECISION_STAGE_FINISH, DECISION_FINISH -> normalized;
            default -> Boolean.TRUE.equals(evaluation.followUpRequired()) ? DECISION_PROBE : DECISION_NEXT;
        };
    }

    /**
     * 返回第一个非空文本。
     */
    private String firstNonBlank(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : second;
    }

    /**
     * 提取短文本。
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public record InitialPlanResult(Map<String, Object> plan, Map<String, Object> firstQuestion) {
    }

    public record EvaluationSnapshot(Integer score,
                                     Map<String, Object> feedback,
                                     Boolean followUpRequired,
                                     String followUpQuestion) {
    }

    public record ReflectionResult(String decision,
                                   String reason,
                                   Boolean followUpRequired,
                                   String followUpQuestion,
                                   Map<String, Object> auditPayload) {
    }
}
