package com.nageoffer.ai.ragent.career.service.demeanor;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DemeanorNormalizationStrategy {

    public DemeanorNormalizedResult normalize(CareerDemeanorAnalysisProviderResult workflowResult,
                                              DemeanorFaceSignal faceSignal,
                                              List<CareerDemeanorObservation> observations) {
        Set<String> signals = new LinkedHashSet<>();
        List<Double> confidences = new ArrayList<>();
        if (workflowResult != null) {
            if (workflowResult.signals() != null) {
                workflowResult.signals().stream()
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .forEach(signals::add);
            }
            Integer score = workflowResult.compositeScore();
            if (score != null) {
                int compositeScore = Math.max(0, Math.min(100, score));
                signals.add("composite-score:" + compositeScore);
                confidences.add(compositeScore / 100D);
            }
        }
        if (faceSignal != null) {
            signals.add("face-count:" + Math.max(0, faceSignal.faceCount()));
            String emotion = normalizeEmotion(faceSignal.dominantEmotion());
            if (StrUtil.isNotBlank(emotion)) {
                signals.add("face-emotion:" + emotion);
            }
            double faceConfidence = clamp(faceSignal.averageConfidence());
            signals.add("face-confidence:" + round2Text(faceConfidence));
            if (faceConfidence > 0D) {
                confidences.add(faceConfidence);
            }
        }
        double observationConfidence = observationConfidence(observations);
        if (observationConfidence > 0D && confidences.isEmpty()) {
            confidences.add(observationConfidence);
        }
        double confidence = confidences.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        return new DemeanorNormalizedResult(round2(confidence), List.copyOf(signals));
    }

    private double observationConfidence(List<CareerDemeanorObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return 0D;
        }
        return observations.stream()
                .map(CareerDemeanorObservation::confidence)
                .filter(value -> value != null && value >= 0D)
                .mapToDouble(this::clamp)
                .average()
                .orElse(0D);
    }

    private String normalizeEmotion(String value) {
        String emotion = StrUtil.blankToDefault(value, "").trim().toLowerCase(Locale.ROOT);
        return switch (emotion) {
            case "calm", "neutral", "normal" -> "calm-expression";
            case "happy", "smile", "positive" -> "positive-expression";
            case "angry", "fear", "sad", "anxious", "panic" -> "risk-expression:" + emotion;
            default -> emotion;
        };
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String round2Text(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
