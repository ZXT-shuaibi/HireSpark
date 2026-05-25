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

package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.controller.vo.CareerRadarItemVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.service.report.WeightedRadarComputationStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedRadarComputationStrategyTest {

    private final WeightedRadarComputationStrategy strategy = new WeightedRadarComputationStrategy();

    @Test
    void computeBuildsWeightedRadarFromTurnsAndFeedbackSignals() {
        List<InterviewTurnDO> turns = List.of(
                turn(1, "TECHNICAL", 82, "{\"strengths\":[\"结构清晰\"],\"weaknesses\":[\"细节少\"],\"missingPoints\":[\"缓存\"],\"risks\":[\"超时\"]}"),
                turn(2, "PROJECT_DEEP_DIVE", 90, "{\"strengths\":[\"结果好\"],\"weaknesses\":[],\"missingPoints\":[],\"risks\":[]}")
        );

        List<CareerRadarItemVO> radar = strategy.compute(turns, Map.of("radar", List.of(Map.of("dimension", "llm"))));

        assertEquals(5, radar.size());
        assertEquals("专业技能", radar.get(0).getDimension());
        assertEquals(35, radar.get(0).getWeight());
        assertTrue(radar.stream().allMatch(item -> "WEIGHTED_RADAR_COMPUTATION".equals(item.getSource())));
        assertFalse(radar.stream().anyMatch(item -> item.getScore() == null));
    }

    @Test
    void computeReturnsEmptyRadarWhenNoScoredTurnsExist() {
        List<CareerRadarItemVO> radar = strategy.compute(List.of(
                turn(1, "TECHNICAL", null, "{}")
        ), Map.of());

        assertTrue(radar.isEmpty());
    }

    private InterviewTurnDO turn(int turnNo, String turnType, Integer score, String feedbackJson) {
        return InterviewTurnDO.builder()
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(turnNo)
                .turnType(turnType)
                .score(score)
                .feedbackJson(feedbackJson)
                .build();
    }
}
