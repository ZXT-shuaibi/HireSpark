package com.nageoffer.ai.ragent.career.service.demeanor;

public record DemeanorSignal(String source,
                             String type,
                             String label,
                             Double score,
                             String provider) {
}
