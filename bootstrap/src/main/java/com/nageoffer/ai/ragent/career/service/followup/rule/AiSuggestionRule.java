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

import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecision;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecisionRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AI 建议节点，只有明确要求追问且问题非空时才采纳。
 */
@Order(30)
@Component
public class AiSuggestionRule implements FollowUpDecisionRule {

    /**
     * 判断 AI 追问建议是否可直接创建追问。
     */
    @Override
    public Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request) {
        String question = FollowUpRuleSupport.trimToNull(request == null ? null : request.llmFollowUpQuestion());
        if (request != null && request.llmFollowUpRequired() && question != null) {
            return Optional.of(InterviewFollowUpDecision.required(
                    question,
                    "AI 明确建议追问且问题非空",
                    "LLM_SUGGESTION"));
        }
        return Optional.empty();
    }
}
