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

import com.nageoffer.ai.ragent.career.enums.InterviewTurnType;
import com.nageoffer.ai.ragent.career.service.followup.CareerInterviewFollowUpProperties;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecision;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecisionRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 追问次数上限节点，达到配置上限后优先终止后续追问判断。
 */
@Order(20)
@Component
public class FollowUpLimitRule implements FollowUpDecisionRule {

    private final CareerInterviewFollowUpProperties properties;

    /**
     * 注入面试追问配置，用于读取追问次数上限。
     */
    public FollowUpLimitRule(CareerInterviewFollowUpProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断同一会话中 FOLLOW_UP 轮次是否已达到配置上限。
     */
    @Override
    public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
        int maxFollowUpCount = properties.effectiveMaxFollowUpCount();
        long followUpCount = FollowUpRuleSupport.safeTurns(request).stream()
                .filter(turn -> InterviewTurnType.FOLLOW_UP.name().equals(turn.getTurnType()))
                .count();
        if (followUpCount >= maxFollowUpCount) {
            return Optional.of(InterviewFollowUpDecision.skipped(
                    "同一会话追问次数已达到配置上限（" + maxFollowUpCount + " 次）",
                    "FOLLOW_UP_LIMIT"));
        }
        return Optional.empty();
    }
}
