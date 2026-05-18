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

package com.nageoffer.ai.ragent.career.service.followup;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.enums.InterviewTurnType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认追问决策服务，以节点式规则链按顺序判断追问上限、LLM 建议、反馈缺口和低分兜底。
 */
@Service
public class DefaultInterviewFollowUpDecisionService implements InterviewFollowUpDecisionService {

    private static final int DEFAULT_MAX_FOLLOW_UP_COUNT = 2;
    private static final int DEFAULT_LOW_SCORE_THRESHOLD = 60;
    private static final String NO_FOLLOW_UP_RULE = "NO_FOLLOW_UP";
    private static final String LOW_SCORE_FALLBACK_QUESTION = "能否再补充一个关键细节，说明你的思路或取舍？";

    private final List<FollowUpDecisionRule> rules = List.of(
            new FollowUpLimitRule(),
            new LlmSuggestionRule(),
            new FeedbackGapRule(),
            new LowScoreRule()
    );

    /**
     * 按 LiteFlow 风格节点式规则链计算当前轮次是否需要追问。
     */
    @Override
    public InterviewFollowUpDecision decide(InterviewFollowUpDecisionRequest request) {
        for (FollowUpDecisionRule rule : rules) {
            Optional<InterviewFollowUpDecision> decision = rule.apply(request);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        return InterviewFollowUpDecision.skipped("未命中追问规则", NO_FOLLOW_UP_RULE);
    }

    /**
     * 规则节点接口，单个节点只负责判断一种追问条件。
     */
    private interface FollowUpDecisionRule {

        /**
         * 尝试根据当前请求产出追问决策，未命中时交给后续节点。
         */
        Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request);
    }

    /**
     * 追问次数上限节点，达到默认上限后终止后续追问。
     */
    private static class FollowUpLimitRule implements FollowUpDecisionRule {

        /**
         * 判断同一会话中 FOLLOW_UP 轮次是否已达到默认上限。
         */
        @Override
        public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
            long followUpCount = safeTurns(request).stream()
                    .filter(turn -> InterviewTurnType.FOLLOW_UP.name().equals(turn.getTurnType()))
                    .count();
            if (followUpCount >= DEFAULT_MAX_FOLLOW_UP_COUNT) {
                return Optional.of(InterviewFollowUpDecision.skipped("同一会话追问次数已达到上限", "FOLLOW_UP_LIMIT"));
            }
            return Optional.empty();
        }
    }

    /**
     * LLM 建议节点，只有明确要求追问且问题非空时才采纳。
     */
    private static class LlmSuggestionRule implements FollowUpDecisionRule {

        /**
         * 判断 LLM 追问建议是否可直接创建追问。
         */
        @Override
        public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
            String question = trimToNull(request == null ? null : request.llmFollowUpQuestion());
            if (request != null && request.llmFollowUpRequired() && question != null) {
                return Optional.of(InterviewFollowUpDecision.required(question, "LLM 明确建议追问", "LLM_SUGGESTION"));
            }
            return Optional.empty();
        }
    }

    /**
     * 反馈缺口节点，根据 weaknesses、missingPoints 或 risks 生成简短追问。
     */
    private static class FeedbackGapRule implements FollowUpDecisionRule {

        /**
         * 判断反馈中的缺失点或风险项是否足以触发追问。
         */
        @Override
        public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
            String gap = firstFeedbackGap(request == null ? null : request.feedback());
            if (gap == null) {
                return Optional.empty();
            }
            return Optional.of(InterviewFollowUpDecision.required(
                    "请简要补充：" + gap + "。",
                    "评分反馈存在待补充项",
                    "FEEDBACK_GAP"));
        }
    }

    /**
     * 低分兜底节点，在没有可用 LLM 问题时生成保守追问。
     */
    private static class LowScoreRule implements FollowUpDecisionRule {

        /**
         * 判断评分是否低于默认阈值并生成兜底追问。
         */
        @Override
        public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
            if (request != null && request.score() != null && request.score() < DEFAULT_LOW_SCORE_THRESHOLD) {
                return Optional.of(InterviewFollowUpDecision.required(
                        LOW_SCORE_FALLBACK_QUESTION,
                        "评分低于默认阈值",
                        "LOW_SCORE"));
            }
            return Optional.empty();
        }
    }

    /**
     * 返回安全的会话轮次列表，避免空请求导致规则节点异常。
     */
    private static List<InterviewTurnDO> safeTurns(InterviewFollowUpDecisionRequest request) {
        if (request == null || request.sessionTurns() == null) {
            return List.of();
        }
        return request.sessionTurns();
    }

    /**
     * 从反馈字段中提取第一个非空缺失点。
     */
    private static String firstFeedbackGap(Map<String, Object> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return null;
        }
        for (String key : List.of("weaknesses", "missingPoints", "risks")) {
            for (Object value : valuesOf(feedback.get(key))) {
                String text = trimToNull(value == null ? null : String.valueOf(value));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * 将反馈字段统一转换为列表，兼容单值和集合值。
     */
    private static List<Object> valuesOf(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    /**
     * 去除字符串首尾空白并将空字符串转换为 null。
     */
    private static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
