package com.nageoffer.ai.ragent.career.service.tts;

import java.util.List;

public record CareerTextToSpeechPlan(boolean enabled,
                                     String status,
                                     List<String> chunks,
                                     String cacheKey,
                                     String cancelKey,
                                     String fallbackText,
                                     String degradeReason,
                                     String voice,
                                     int cacheTtlSeconds) {
}
