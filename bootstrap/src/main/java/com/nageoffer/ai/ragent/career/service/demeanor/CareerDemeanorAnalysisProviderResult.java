package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record CareerDemeanorAnalysisProviderResult(Integer panicLevel,
                                                   Integer seriousnessLevel,
                                                   Integer emoticonHandling,
                                                   Integer compositeScore,
                                                   List<String> signals,
                                                   String providerMessage) {
}
