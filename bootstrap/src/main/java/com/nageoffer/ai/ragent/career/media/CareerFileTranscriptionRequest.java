package com.nageoffer.ai.ragent.career.media;

public record CareerFileTranscriptionRequest(String fileName,
                                             String contentType,
                                             byte[] audioBytes,
                                             String language,
                                             String traceId) {
}
