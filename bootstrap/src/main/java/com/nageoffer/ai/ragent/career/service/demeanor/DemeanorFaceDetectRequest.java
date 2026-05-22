package com.nageoffer.ai.ragent.career.service.demeanor;

public record DemeanorFaceDetectRequest(String imageBase64,
                                        String imageFormat,
                                        String traceId) {
}
