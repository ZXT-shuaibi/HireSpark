package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record CareerDemeanorAnalysisRequest(String sessionId,
                                            boolean consentGranted,
                                            List<CareerDemeanorObservation> observations) {
}
