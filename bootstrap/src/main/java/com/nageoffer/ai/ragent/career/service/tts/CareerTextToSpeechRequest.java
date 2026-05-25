package com.nageoffer.ai.ragent.career.service.tts;

public record CareerTextToSpeechRequest(String sessionId, String turnId, String text) {

    public static CareerTextToSpeechRequest of(String sessionId, String turnId, String text) {
        return new CareerTextToSpeechRequest(sessionId, turnId, text);
    }
}
