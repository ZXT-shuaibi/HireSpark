package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record DemeanorNormalizedResult(double confidence,
                                       List<String> signals) {
}
