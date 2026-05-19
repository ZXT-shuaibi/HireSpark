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

package com.nageoffer.ai.ragent.career.service.report;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerRadarItemVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WeightedRadarComputationStrategy implements CareerRadarComputationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String SOURCE = "WEIGHTED_RADAR_COMPUTATION";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 以面试评分为主、反馈风险为修正项计算雷达图，避免只信任模型自由输出。
     */
    @Override
    public List<CareerRadarItemVO> compute(List<InterviewTurnDO> scoredTurns, Map<String, Object> llmReport) {
        List<InterviewTurnDO> turns = scoredTurns == null ? List.of() : scoredTurns.stream()
                .filter(turn -> turn != null && turn.getScore() != null)
                .toList();
        if (turns.isEmpty()) {
            return List.of();
        }
        FeedbackStats stats = collectFeedbackStats(turns);
        int average = average(turns);
        List<CareerRadarItemVO> result = new ArrayList<>();
        result.add(item("专业技能", scoreByType(turns, average, "TECHNICAL", "FOLLOW_UP", -stats.missingPenalty()), 35,
                "基于技术题、追问得分与知识点遗漏计算"));
        result.add(item("项目表达", scoreByType(turns, average, "PROJECT_DEEP_DIVE", null, stats.strengthBonus()), 25,
                "基于项目深挖题和有效亮点计算"));
        result.add(item("问题分析", clamp(average - stats.missingPenalty() - stats.weaknessPenalty()), 20,
                "基于平均分、短板和遗漏点计算"));
        result.add(item("风险控制", clamp(average - stats.riskPenalty()), 10,
                "基于面试反馈中的风险项扣分计算"));
        result.add(item("潜力指数", clamp(average + stats.strengthBonus() - stats.riskPenalty() / 2), 10,
                "基于综合得分、亮点数量和风险扣减计算"));
        return result;
    }

    /**
     * 创建单个雷达维度结果。
     */
    private CareerRadarItemVO item(String dimension, int score, int weight, String comment) {
        return CareerRadarItemVO.builder()
                .dimension(dimension)
                .score(clamp(score))
                .weight(weight)
                .comment(comment)
                .source(SOURCE)
                .build();
    }

    /**
     * 计算指定题型的平均分，并按反馈信号做加减修正。
     */
    private int scoreByType(List<InterviewTurnDO> turns,
                            int fallback,
                            String primaryType,
                            String secondaryType,
                            int adjustment) {
        List<InterviewTurnDO> matched = turns.stream()
                .filter(turn -> primaryType.equals(turn.getTurnType())
                        || (secondaryType != null && secondaryType.equals(turn.getTurnType())))
                .toList();
        int base = matched.isEmpty() ? fallback : average(matched);
        return clamp(base + adjustment);
    }

    /**
     * 汇总面试反馈中的亮点、短板、遗漏和风险数量。
     */
    private FeedbackStats collectFeedbackStats(List<InterviewTurnDO> turns) {
        int strengths = 0;
        int weaknesses = 0;
        int missingPoints = 0;
        int risks = 0;
        for (InterviewTurnDO turn : turns) {
            Map<String, Object> feedback = readFeedback(turn.getFeedbackJson());
            strengths += countList(feedback.get("strengths"));
            weaknesses += countList(feedback.get("weaknesses"));
            missingPoints += countList(feedback.get("missingPoints"));
            risks += countList(feedback.get("risks"));
        }
        return new FeedbackStats(strengths, weaknesses, missingPoints, risks);
    }

    /**
     * 解析轮次反馈 JSON，解析失败时按空反馈处理。
     */
    private Map<String, Object> readFeedback(String feedbackJson) {
        if (StrUtil.isBlank(feedbackJson)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(feedbackJson, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 统计列表型反馈字段数量，兼容单值字段。
     */
    private int countList(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty() ? 0 : 1;
        }
        if (value == null) {
            return 0;
        }
        return 1;
    }

    /**
     * 计算面试轮次平均分。
     */
    private int average(List<InterviewTurnDO> turns) {
        double average = turns.stream()
                .map(InterviewTurnDO::getScore)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0D);
        return clamp(Math.round((float) average));
    }

    /**
     * 将分数限制在 0 到 100 之间。
     */
    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private record FeedbackStats(int strengths,
                                 int weaknesses,
                                 int missingPoints,
                                 int risks) {

        /**
         * 根据亮点数量给出上限受控的加分。
         */
        private int strengthBonus() {
            return Math.min(8, strengths * 2);
        }

        /**
         * 根据短板数量给出上限受控的扣分。
         */
        private int weaknessPenalty() {
            return Math.min(12, weaknesses * 3);
        }

        /**
         * 根据遗漏知识点数量给出上限受控的扣分。
         */
        private int missingPenalty() {
            return Math.min(15, missingPoints * 4);
        }

        /**
         * 根据风险数量给出上限受控的扣分。
         */
        private int riskPenalty() {
            return Math.min(20, risks * 5);
        }
    }
}
