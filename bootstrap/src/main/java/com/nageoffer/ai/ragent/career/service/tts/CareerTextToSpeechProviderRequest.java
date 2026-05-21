package com.nageoffer.ai.ragent.career.service.tts;

import java.util.List;

public record CareerTextToSpeechProviderRequest(String sessionId,
                                                String turnId,
                                                String text,
                                                List<String> chunks,
                                                String voice,
                                                String cacheKey,
                                                String cancelKey) {
}
