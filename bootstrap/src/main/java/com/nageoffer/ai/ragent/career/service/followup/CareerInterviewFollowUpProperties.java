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
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 面试追问规则配置，集中管理上限、低分阈值和低分兜底问题。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "career.interview.follow-up")
public class CareerInterviewFollowUpProperties {

    private static final int DEFAULT_MAX_FOLLOW_UP_COUNT = 2;
    private static final int DEFAULT_LOW_SCORE_THRESHOLD = 60;
    private static final String DEFAULT_LOW_SCORE_FALLBACK_QUESTION = "能否再补充一个关键细节，说明你的思路或取舍？";
    private static final List<String> DEFAULT_RULE_CHAIN = List.of(
            "COMPLETED_STATE_GUARD",
            "FOLLOW_UP_LIMIT",
            "LLM_SUGGESTION",
            "FEEDBACK_GAP",
            "LOW_SCORE");

    /**
     * 单个会话允许创建的最大追问次数。
     */
    private Integer maxFollowUpCount = DEFAULT_MAX_FOLLOW_UP_COUNT;

    /**
     * 触发低分兜底追问的评分阈值。
     */
    private Integer lowScoreThreshold = DEFAULT_LOW_SCORE_THRESHOLD;

    /**
     * 低分但无明确追问问题时使用的兜底追问。
     */
    private String lowScoreFallbackQuestion = DEFAULT_LOW_SCORE_FALLBACK_QUESTION;

    /**
     * LiteFlow 风格的追问规则节点顺序。节点 ID 未识别时会被忽略，空配置回退默认链。
     */
    private List<String> ruleChain = DEFAULT_RULE_CHAIN;

    /**
     * 返回生效的追问次数上限，允许配置为 0 表示完全不生成追问。
     */
    public int effectiveMaxFollowUpCount() {
        if (maxFollowUpCount == null) {
            return DEFAULT_MAX_FOLLOW_UP_COUNT;
        }
        return Math.max(0, maxFollowUpCount);
    }

    /**
     * 返回生效的低分阈值，缺省时使用内置默认值。
     */
    public int effectiveLowScoreThreshold() {
        if (lowScoreThreshold == null) {
            return DEFAULT_LOW_SCORE_THRESHOLD;
        }
        return lowScoreThreshold;
    }

    /**
     * 返回生效的低分兜底追问，空白配置会回退到内置默认问题。
     */
    public String effectiveLowScoreFallbackQuestion() {
        return StrUtil.blankToDefault(lowScoreFallbackQuestion, DEFAULT_LOW_SCORE_FALLBACK_QUESTION).trim();
    }

    /**
     * 返回生效的规则节点顺序，允许 YAML 后续调整链路而不改 Java 代码。
     */
    public List<String> effectiveRuleChain() {
        if (ruleChain == null || ruleChain.isEmpty()) {
            return DEFAULT_RULE_CHAIN;
        }
        return ruleChain.stream()
                .filter(StrUtil::isNotBlank)
                .map(item -> item.trim().toUpperCase())
                .toList();
    }
}
