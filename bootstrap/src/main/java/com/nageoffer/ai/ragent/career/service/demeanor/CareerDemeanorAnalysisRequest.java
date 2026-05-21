package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record CareerDemeanorAnalysisRequest(String sessionId,
                                            boolean consentGranted,
                                            List<CareerDemeanorObservation> observations,
                                            String imageUrl,
                                            String imageBase64,
                                            String sampledAt) {

    public CareerDemeanorAnalysisRequest(String sessionId,
                                         boolean consentGranted,
                                         List<CareerDemeanorObservation> observations) {
        this(sessionId, consentGranted, observations, "", "", "");
    }

    public CareerDemeanorAnalysisRequest(String sessionId,
                                         boolean consentGranted,
                                         List<CareerDemeanorObservation> observations,
                                         String imageUrl,
                                         String imageBase64) {
        this(sessionId, consentGranted, observations, imageUrl, imageBase64, "");
    }
}
