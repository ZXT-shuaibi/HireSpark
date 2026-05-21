package com.nageoffer.ai.ragent.career.service.demeanor;

import java.util.List;

public record CareerDemeanorAnalysisProviderRequest(String sessionId,
                                                    String imageUrl,
                                                    String imageBase64,
                                                    String sampledAt,
                                                    List<CareerDemeanorObservation> observations) {
}
