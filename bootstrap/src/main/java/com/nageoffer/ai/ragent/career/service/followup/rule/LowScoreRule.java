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

package com.nageoffer.ai.ragent.career.service.followup.rule;

import com.nageoffer.ai.ragent.career.service.followup.CareerInterviewFollowUpProperties;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecision;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecisionRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 低分兜底节点，在没有可用 AI 问题和反馈缺口时生成保守追问。
 */
@Order(50)
@Component
public class LowScoreRule implements FollowUpDecisionRule {

    private final CareerInterviewFollowUpProperties properties;

    /**
     * 注入面试追问配置，用于读取低分阈值和兜底问题。
     */
    public LowScoreRule(CareerInterviewFollowUpProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断评分是否低于配置阈值并生成兜底追问。
     */
    @Override
    public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
        if (request == null || request.score() == null) {
            return Optional.empty();
        }
        int threshold = properties.effectiveLowScoreThreshold();
        if (request.score() >= threshold) {
            return Optional.empty();
        }
        return Optional.of(InterviewFollowUpDecision.required(
                properties.effectiveLowScoreFallbackQuestion(),
                "评分 " + request.score() + " 低于配置阈值 " + threshold,
                "LOW_SCORE"));
    }
}
