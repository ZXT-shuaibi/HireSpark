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

import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecision;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecisionRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * 会话终态守卫节点，已完成或已取消的会话不再生成追问。
 */
@Order(20)
@Component
public class CompletedStateGuardRule implements FollowUpDecisionRule {

    /**
     * 判断会话是否处于完成或取消状态，命中时直接跳过追问。
     */
    @Override
    public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
        InterviewSessionStatus status = parseStatus(request == null ? null : request.sessionStatus());
        if (status == null || !status.terminal()) {
            return Optional.empty();
        }
        return Optional.of(InterviewFollowUpDecision.skipped(
                terminalReason(status),
                "COMPLETED_STATE_GUARD"));
    }

    /**
     * 解析会话状态，空值或未知值不命中终态守卫。
     */
    private InterviewSessionStatus parseStatus(String value) {
        String text = FollowUpRuleSupport.trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return InterviewSessionStatus.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 生成终态守卫命中的中文原因。
     */
    private String terminalReason(InterviewSessionStatus status) {
        if (status == InterviewSessionStatus.CANCELLED) {
            return "面试会话已取消，不再生成追问";
        }
        return "面试会话已完成，不再生成追问";
    }
}
