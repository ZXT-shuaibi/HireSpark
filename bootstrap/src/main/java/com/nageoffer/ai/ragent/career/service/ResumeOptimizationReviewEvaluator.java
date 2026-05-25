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

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationSuggestionDO;
import com.nageoffer.ai.ragent.career.enums.OptimizationReviewStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResumeOptimizationReviewEvaluator {

    private static final double PASSING_QUALITY_SCORE = 0.8D;

    public Decision evaluate(Map<String, Object> output, List<ResumeOptimizationSuggestionDO> suggestions) {
        Map<String, Object> review = extractReviewMap(output);
        Double qualityScore = extractDouble(firstNonNull(
                review.get("qualityScore"),
                output.get("qualityScore")
        ));
        boolean truthfulnessRisk = extractBoolean(firstNonNull(
                review.get("truthfulnessRisk"),
                output.get("truthfulnessRisk")
        ));
        boolean unsupportedClaims = extractBoolean(firstNonNull(
                review.get("unsupportedClaims"),
                output.get("unsupportedClaims"),
                output.get("unsupported")
        ));

        boolean highRiskSuggestion = suggestions != null && suggestions.stream()
                .map(ResumeOptimizationSuggestionDO::getRiskLevel)
                .filter(StrUtil::isNotBlank)
                .map(riskLevel -> riskLevel.trim().toUpperCase(Locale.ROOT))
                .anyMatch("HIGH"::equals);
        boolean hasRisk = truthfulnessRisk || unsupportedClaims || highRiskSuggestion;
        double score = qualityScore == null ? 0D : qualityScore;
        OptimizationReviewStatus status;
        if (hasRisk) {
            status = OptimizationReviewStatus.BLOCKED_BY_RISK;
        } else if (score <= PASSING_QUALITY_SCORE) {
            status = OptimizationReviewStatus.NEEDS_REVISION;
        } else {
            status = OptimizationReviewStatus.PASSED;
        }
        return new Decision(score, hasRisk, status, buildRiskSummary(hasRisk, unsupportedClaims, highRiskSuggestion));
    }

    private Map<String, Object> extractReviewMap(Map<String, Object> output) {
        if (output == null) {
            return Map.of();
        }
        Object review = firstNonNull(output.get("review"), output.get("reviewer"));
        if (review instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue,
                            (left, right) -> right
                    ));
        }
        return Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double extractDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean extractBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "yes".equalsIgnoreCase(text.trim());
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return false;
    }

    private String buildRiskSummary(boolean hasRisk, boolean unsupportedClaims, boolean highRiskSuggestion) {
        if (!hasRisk) {
            return null;
        }
        if (unsupportedClaims) {
            return "Detected unsupported resume claims";
        }
        if (highRiskSuggestion) {
            return "Detected HIGH risk optimization suggestion";
        }
        return "Detected truthfulness risk";
    }

    public record Decision(double qualityScore,
                           boolean truthfulnessRisk,
                           OptimizationReviewStatus status,
                           String riskSummary) {
    }
}
