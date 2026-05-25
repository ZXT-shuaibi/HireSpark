package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record CareerDemeanorAnalysisResult(boolean enabled,
                                           String status,
                                           boolean includedInScore,
                                           double confidence,
                                           List<String> signals,
                                           List<DemeanorSignal> structuredSignals,
                                           List<String> limitations,
                                           String retentionPolicy) {

    public CareerDemeanorAnalysisResult(boolean enabled,
                                        String status,
                                        boolean includedInScore,
                                        double confidence,
                                        List<String> signals,
                                        List<String> limitations,
                                        String retentionPolicy) {
        this(enabled, status, includedInScore, confidence, signals, List.of(), limitations, retentionPolicy);
    }
}
