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

import com.nageoffer.ai.ragent.career.service.followup.rule.AiSuggestionRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.CompletedStateGuardRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.FollowUpDecisionRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.FollowUpLimitRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.LowScoreRule;
import com.nageoffer.ai.ragent.career.service.followup.rule.MissingPointsRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 默认追问决策服务，按配置好的节点式规则链产出追问决策和命中审计信息。
 */
@Service
public class DefaultInterviewFollowUpDecisionService implements InterviewFollowUpDecisionService {

    private static final String NO_FOLLOW_UP_RULE = "NO_FOLLOW_UP";

    private final List<FollowUpDecisionRule> rules;

    /**
     * 使用默认配置创建追问决策服务，兼容不经过 Spring 容器的单元测试。
     */
    public DefaultInterviewFollowUpDecisionService() {
        this(defaultRules());
    }

    /**
     * 注入按顺序排列的追问决策规则节点。
     */
    @Autowired
    public DefaultInterviewFollowUpDecisionService(List<FollowUpDecisionRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * 按节点式规则链计算当前轮次是否需要追问。
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
     * 创建默认规则链，保持不经过 Spring 容器时的规则顺序和默认行为。
     */
    private static List<FollowUpDecisionRule> defaultRules() {
        CareerInterviewFollowUpProperties properties = new CareerInterviewFollowUpProperties();
        return List.of(
                new CompletedStateGuardRule(),
                new FollowUpLimitRule(properties),
                new AiSuggestionRule(),
                new MissingPointsRule(),
                new LowScoreRule(properties));
    }
}
