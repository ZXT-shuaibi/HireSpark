package com.nageoffer.ai.ragent.career.service.demeanor;

public record DemeanorFaceSignal(String provider,
                                 String sid,
                                 int faceCount,
                                 String dominantEmotion,
                                 double averageConfidence) {
}
