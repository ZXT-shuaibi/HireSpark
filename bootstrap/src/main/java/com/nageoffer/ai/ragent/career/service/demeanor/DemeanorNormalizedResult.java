package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record DemeanorNormalizedResult(double confidence,
                                       List<String> signals,
                                       List<DemeanorSignal> structuredSignals) {

    public DemeanorNormalizedResult(double confidence, List<String> signals) {
        this(confidence, signals, List.of());
    }
}
