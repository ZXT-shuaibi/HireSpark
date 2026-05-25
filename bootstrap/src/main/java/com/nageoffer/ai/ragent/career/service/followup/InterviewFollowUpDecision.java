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

/**
 * 追问决策结果，描述是否追问、问题文本、决策原因和命中的规则。
 */
public record InterviewFollowUpDecision(boolean required,
                                        String question,
                                        String reason,
                                        String matchedRule) {

    /**
     * 创建需要追问的决策结果。
     */
    public static InterviewFollowUpDecision required(String question, String reason, String matchedRule) {
        return new InterviewFollowUpDecision(true, question, reason, matchedRule);
    }

    /**
     * 创建不需要追问的决策结果。
     */
    public static InterviewFollowUpDecision skipped(String reason, String matchedRule) {
        return new InterviewFollowUpDecision(false, null, reason, matchedRule);
    }
}
