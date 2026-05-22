package com.nageoffer.ai.ragent.career.service.xunfei;

public record XunfeiFaceDetectResult(String provider,
                                     String sid,
                                     int faceCount,
                                     String dominantEmotion,
                                     double averageConfidence,
                                     String rawJson) {
}
