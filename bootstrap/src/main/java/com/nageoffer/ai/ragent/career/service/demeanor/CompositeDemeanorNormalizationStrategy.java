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
public class CompositeDemeanorNormalizationStrategy implements DemeanorNormalizationStrategy {

    private static final String SOURCE_FACE_DETECT = "FACE_DETECT";
    private static final String SOURCE_XINGCHEN = "XINGCHEN_WORKFLOW";
    private static final String SOURCE_OBSERVATION = "OBSERVATION";

    @Override
    public DemeanorNormalizedResult normalize(CareerDemeanorAnalysisProviderResult workflowResult,
                                              DemeanorFaceSignal faceSignal,
                                              List<CareerDemeanorObservation> observations) {
        Set<String> labels = new LinkedHashSet<>();
        List<DemeanorSignal> structuredSignals = new ArrayList<>();
        Double workflowConfidence = workflowConfidence(workflowResult, labels, structuredSignals);
        Double faceConfidence = faceConfidence(faceSignal, labels, structuredSignals);
        double observationConfidence = observationConfidence(observations, labels, structuredSignals);
        double confidence = weightedConfidence(workflowConfidence, faceConfidence, observationConfidence);
        return new DemeanorNormalizedResult(round2(confidence), List.copyOf(labels), List.copyOf(structuredSignals));
    }

    private Double workflowConfidence(CareerDemeanorAnalysisProviderResult workflowResult,
                                      Set<String> labels,
                                      List<DemeanorSignal> structuredSignals) {
        if (workflowResult == null) {
            return null;
        }
        if (workflowResult.signals() != null) {
            workflowResult.signals().stream()
                    .filter(StrUtil::isNotBlank)
                    .map(String::trim)
                    .forEach(signal -> {
                        labels.add(signal);
                        structuredSignals.add(new DemeanorSignal(SOURCE_XINGCHEN, "workflow-signal",
                                signal, null, null));
                    });
        }
        Integer score = workflowResult.compositeScore();
        if (score == null) {
            return null;
        }
        int compositeScore = Math.max(0, Math.min(100, score));
        String label = SIGNAL_COMPOSITE_SCORE + ":" + compositeScore;
        labels.add(label);
        structuredSignals.add(new DemeanorSignal(SOURCE_XINGCHEN, "composite-score",
                label, (double) compositeScore, null));
        return compositeScore / 100D;
    }

    private Double faceConfidence(DemeanorFaceSignal faceSignal,
                                  Set<String> labels,
                                  List<DemeanorSignal> structuredSignals) {
        if (faceSignal == null) {
            return null;
        }
        String provider = StrUtil.isBlank(faceSignal.provider()) ? null : faceSignal.provider().trim();
        String faceCountLabel = "face-count:" + Math.max(0, faceSignal.faceCount());
        labels.add(faceCountLabel);
        structuredSignals.add(new DemeanorSignal(SOURCE_FACE_DETECT, "face-count",
                faceCountLabel, (double) Math.max(0, faceSignal.faceCount()), provider));
        String emotion = normalizeEmotion(faceSignal.dominantEmotion());
        if (StrUtil.isNotBlank(emotion)) {
            String label = SIGNAL_FACE_EMOTION + ":" + emotion;
            labels.add(label);
            structuredSignals.add(new DemeanorSignal(SOURCE_FACE_DETECT, "expression",
                    label, null, provider));
        }
        double confidence = clamp(faceSignal.averageConfidence());
        String confidenceLabel = "face-confidence:" + round2Text(confidence);
        labels.add(confidenceLabel);
        structuredSignals.add(new DemeanorSignal(SOURCE_FACE_DETECT, "confidence",
                confidenceLabel, round2(confidence * 100D), provider));
        return confidence;
    }

    private double observationConfidence(List<CareerDemeanorObservation> observations,
                                         Set<String> labels,
                                         List<DemeanorSignal> structuredSignals) {
        if (observations == null || observations.isEmpty()) {
            return 0D;
        }
        List<Double> confidences = new ArrayList<>();
        for (CareerDemeanorObservation observation : observations) {
            if (observation == null || StrUtil.isBlank(observation.signal())) {
                continue;
            }
            String label = observation.signal().trim();
            labels.add(label);
            Double confidence = observation.confidence() == null ? null : clamp(observation.confidence()) * 100D;
            structuredSignals.add(new DemeanorSignal(SOURCE_OBSERVATION, "observation",
                    label, confidence == null ? null : round2(confidence), null));
            if (observation.confidence() != null && observation.confidence() >= 0D) {
                confidences.add(clamp(observation.confidence()));
            }
        }
        return confidences.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double weightedConfidence(Double workflowConfidence, Double faceConfidence, double observationConfidence) {
        if (workflowConfidence != null && faceConfidence != null) {
            return workflowConfidence * 0.6D + faceConfidence * 0.4D;
        }
        if (workflowConfidence != null) {
            return workflowConfidence;
        }
        if (faceConfidence != null) {
            return faceConfidence;
        }
        return observationConfidence;
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
