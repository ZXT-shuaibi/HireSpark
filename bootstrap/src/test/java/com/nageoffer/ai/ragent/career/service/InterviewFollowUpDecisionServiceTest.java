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

import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.service.followup.CareerInterviewFollowUpProperties;
import com.nageoffer.ai.ragent.career.service.followup.DefaultInterviewFollowUpDecisionService;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecision;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecisionRequest;
import com.nageoffer.ai.ragent.career.service.followup.rule.AiSuggestionRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.CompletedStateGuardRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.FollowUpLimitRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.LowScoreRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.MissingPointsRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证面试追问规则链在上限、LLM 建议、低分和反馈缺口场景下的决策结果。
 */
class InterviewFollowUpDecisionServiceTest {

    private final DefaultInterviewFollowUpDecisionService service = serviceWith(new CareerInterviewFollowUpProperties());

    /**
     * 验证同一会话追问达到默认上限后不再继续创建追问。
     */
    @Test
    void followUpLimitStopsAdditionalFollowUp() {
        InterviewFollowUpDecision decision = service.decide(request(85, false, null,
                Map.of("weaknesses", List.of("too shallow")),
                List.of(turn("PROJECT_DEEP_DIVE"), turn("FOLLOW_UP"), turn("FOLLOW_UP"))));

        assertFalse(decision.required());
        assertEquals("FOLLOW_UP_LIMIT", decision.matchedRule());
    }

    /**
     * 验证 LLM 明确给出追问问题时规则链采纳该问题。
     */
    @Test
    void llmSuggestionCreatesFollowUpWhenQuestionIsPresent() {
        InterviewFollowUpDecision decision = service.decide(request(85, true, "Which tradeoff mattered most?",
                Map.of("weaknesses", List.of()),
                List.of(turn("PROJECT_DEEP_DIVE"))));

        assertTrue(decision.required());
        assertEquals("Which tradeoff mattered most?", decision.question());
        assertEquals("LLM_SUGGESTION", decision.matchedRule());
    }

    /**
     * 验证 LLM 只给追问标记但问题为空白时不会创建追问。
     */
    @Test
    void blankLlmQuestionDoesNotCreateFollowUp() {
        InterviewFollowUpDecision decision = service.decide(request(85, true, " ",
                Map.of("weaknesses", List.of()),
                List.of(turn("PROJECT_DEEP_DIVE"))));

        assertFalse(decision.required());
        assertEquals("NO_FOLLOW_UP", decision.matchedRule());
    }

    /**
     * 验证已完成会话即使收到 LLM 追问建议也不会继续生成追问。
     */
    @Test
    void completedSessionStopsFollowUpEvenWhenLlmSuggestsQuestion() {
        InterviewFollowUpDecision decision = service.decide(request(85, true, "Please explain more.",
                Map.of("weaknesses", List.of()),
                List.of(turn("PROJECT_DEEP_DIVE")),
                InterviewSessionStatus.COMPLETED.name()));

        assertFalse(decision.required());
        assertEquals("COMPLETED_STATE_GUARD", decision.matchedRule());
    }

    /**
     * 验证低分且无 LLM 追问问题时生成保守兜底追问。
     */
    @Test
    void lowScoreCreatesConservativeFallbackQuestionWithoutLlmQuestion() {
        InterviewFollowUpDecision decision = service.decide(request(55, false, null,
                Map.of("weaknesses", List.of()),
                List.of(turn("TECHNICAL"))));

        assertTrue(decision.required());
        assertEquals("LOW_SCORE", decision.matchedRule());
        assertEquals("能否再补充一个关键细节，说明你的思路或取舍？", decision.question());
    }

    /**
     * 验证低分阈值使用配置值，配置调高后原本不触发的分数也会触发兜底追问。
     */
    @Test
    void configuredLowScoreThresholdControlsFallbackDecision() {
        CareerInterviewFollowUpProperties properties = new CareerInterviewFollowUpProperties();
        properties.setLowScoreThreshold(70);
        properties.setLowScoreFallbackQuestion("请补充关键决策依据。");
        DefaultInterviewFollowUpDecisionService configuredService = serviceWith(properties);

        InterviewFollowUpDecision decision = configuredService.decide(request(65, false, null,
                Map.of("weaknesses", List.of()),
                List.of(turn("TECHNICAL"))));

        assertTrue(decision.required());
        assertEquals("LOW_SCORE", decision.matchedRule());
        assertEquals("请补充关键决策依据。", decision.question());
    }

    /**
     * 验证低分和反馈缺口同时存在时优先围绕具体缺口追问。
     */
    @Test
    void feedbackGapTakesPriorityWhenLowScoreAlsoExists() {
        InterviewFollowUpDecision decision = service.decide(request(55, false, null,
                Map.of("weaknesses", List.of("缺少限流失败后的补偿说明")),
                List.of(turn("TECHNICAL"))));

        assertTrue(decision.required());
        assertEquals("FEEDBACK_GAP", decision.matchedRule());
        assertEquals("请简要补充：缺少限流失败后的补偿说明。", decision.question());
    }

    /**
     * 验证评分反馈存在缺失点时生成简短追问。
     */
    @Test
    void missingFeedbackPointsCreateShortFallbackQuestion() {
        InterviewFollowUpDecision decision = service.decide(request(80, false, null,
                Map.of("missingPoints", List.of("边界条件处理")),
                List.of(turn("TECHNICAL"))));

        assertTrue(decision.required());
        assertEquals("FEEDBACK_GAP", decision.matchedRule());
        assertEquals("请简要补充：边界条件处理。", decision.question());
    }

    // 构造追问决策请求，便于测试不同规则输入。
    private InterviewFollowUpDecisionRequest request(Integer score,
                                                     boolean llmFollowUpRequired,
                                                     String llmFollowUpQuestion,
                                                     Map<String, Object> feedback,
                                                     List<InterviewTurnDO> turns) {
        return new InterviewFollowUpDecisionRequest(
                turn("TECHNICAL"),
                turns,
                score,
                feedback,
                llmFollowUpRequired,
                llmFollowUpQuestion,
                InterviewSessionStatus.RUNNING.name()
        );
    }

    // 构造指定会话状态的追问决策请求，验证终态守卫节点。
    private InterviewFollowUpDecisionRequest request(Integer score,
                                                     boolean llmFollowUpRequired,
                                                     String llmFollowUpQuestion,
                                                     Map<String, Object> feedback,
                                                     List<InterviewTurnDO> turns,
                                                     String sessionStatus) {
        return new InterviewFollowUpDecisionRequest(
                turn("TECHNICAL"),
                turns,
                score,
                feedback,
                llmFollowUpRequired,
                llmFollowUpQuestion,
                sessionStatus
        );
    }

    // 构造指定类型的面试轮次，供规则统计追问次数。
    private InterviewTurnDO turn(String turnType) {
        return InterviewTurnDO.builder().turnType(turnType).build();
    }

    // 构造包含固定规则顺序的追问决策服务，避免测试依赖 Spring 容器。
    private DefaultInterviewFollowUpDecisionService serviceWith(CareerInterviewFollowUpProperties properties) {
        return new DefaultInterviewFollowUpDecisionService(List.of(
                new FollowUpLimitRule(properties),
                new CompletedStateGuardRule(),
                new AiSuggestionRule(),
                new MissingPointsRule(),
                new LowScoreRule(properties)
        ));
    }
}
