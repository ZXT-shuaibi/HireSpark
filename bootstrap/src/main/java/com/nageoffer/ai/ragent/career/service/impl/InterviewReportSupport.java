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

package com.nageoffer.ai.ragent.career.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewReportVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InterviewReportSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final CareerJsonParser careerJsonParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> toPromptTurns(List<InterviewTurnDO> turns) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (InterviewTurnDO turn : turns) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("turnNo", turn.getTurnNo());
            item.put("turnType", turn.getTurnType());
            item.put("question", turn.getQuestion());
            item.put("answer", turn.getAnswer());
            item.put("score", turn.getScore());
            item.put("feedback", readMap(turn.getFeedbackJson(), "Failed to parse interview feedback JSON"));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> parseReport(String response) {
        try {
            return careerJsonParser.parseObject(response);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException("Failed to parse interview report JSON", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public CareerInterviewReportVO toReportVO(InterviewReportDO report) {
        return toReportVO(
                report,
                readList(report.getRadarJson(), "Failed to parse interview report radar JSON"),
                readList(report.getPlaybackJson(), "Failed to parse interview report playback JSON"),
                readList(report.getSuggestionsJson(), "Failed to parse interview report suggestions JSON")
        );
    }

    public CareerInterviewReportVO toReportVO(InterviewReportDO report,
                                              List<Object> radar,
                                              List<Object> playback,
                                              List<Object> suggestions) {
        return CareerInterviewReportVO.builder()
                .id(report.getId())
                .sessionId(report.getSessionId())
                .overallScore(report.getOverallScore())
                .radar(radar)
                .playback(playback)
                .suggestions(suggestions)
                .summary(report.getSummary())
                .traceId(report.getTraceId())
                .createTime(report.getCreateTime())
                .build();
    }

    public int averageScore(List<InterviewTurnDO> turns) {
        double average = turns.stream()
                .map(InterviewTurnDO::getScore)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0D);
        return clampScore(Math.round((float) average));
    }

    public Integer readValidScore(Object value) {
        Integer score = null;
        if (value instanceof Number number) {
            score = number.intValue();
        } else if (value instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                score = Double.valueOf(text.trim()).intValue();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (score == null || score < 0 || score > 100) {
            return null;
        }
        return score;
    }

    public List<Object> appendFallbackMarker(List<Object> suggestions, int averageScore) {
        List<Object> result = new ArrayList<>(suggestions);
        result.add(scoreFallbackMarker(averageScore));
        return result;
    }

    public List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(value);
    }

    public String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    public String extractString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> readMap(String value, String errorMessage) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private List<Object> readList(String value, String errorMessage) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, LIST_TYPE);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private Map<String, Object> scoreFallbackMarker(int averageScore) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("title", "Overall score fallback");
        marker.put("action", "Generated from average scored interview turns");
        marker.put("priority", "INFO");
        marker.put("source", "AVERAGE_SCORE_FALLBACK");
        marker.put("score", averageScore);
        return marker;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
