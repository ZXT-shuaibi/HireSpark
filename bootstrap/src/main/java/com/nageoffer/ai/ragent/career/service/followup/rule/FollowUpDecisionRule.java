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

import java.util.Optional;

/**
 * 追问决策规则节点接口，单个节点只负责判断一种追问条件。
 */
public interface FollowUpDecisionRule {

    /**
     * 尝试根据当前请求产出追问决策，未命中时返回空交给后续节点。
     */
    Optional<InterviewFollowUpDecision> apply(InterviewFollowUpDecisionRequest request);
}
