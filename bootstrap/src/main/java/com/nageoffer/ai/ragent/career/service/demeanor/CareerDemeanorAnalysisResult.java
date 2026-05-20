package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record CareerDemeanorAnalysisResult(boolean enabled,
                                           String status,
                                           boolean includedInScore,
                                           double confidence,
                                           List<String> signals,
                                           List<String> limitations,
                                           String retentionPolicy) {
}
