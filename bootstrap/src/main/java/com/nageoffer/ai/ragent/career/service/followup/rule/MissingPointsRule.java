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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 反馈缺口节点，根据 weaknesses、missingPoints 或 risks 生成简短追问。
 */
@Order(40)
@Component
public class MissingPointsRule implements FollowUpDecisionRule {

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
                "评分反馈存在待补充项：" + gap,
                "FEEDBACK_GAP"));
    }

    /**
     * 从反馈字段中提取第一个非空缺失点。
     */
    private String firstFeedbackGap(Map<String, Object> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return null;
        }
        for (String key : List.of("weaknesses", "missingPoints", "risks")) {
            for (Object value : FollowUpRuleSupport.valuesOf(feedback.get(key))) {
                String text = FollowUpRuleSupport.trimToNull(value == null ? null : String.valueOf(value));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }
}
