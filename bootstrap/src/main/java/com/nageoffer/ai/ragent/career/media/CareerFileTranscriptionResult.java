package com.nageoffer.ai.ragent.career.media;

public record CareerFileTranscriptionResult(String provider,
                                            String status,
                                            String text,
                                            String taskId,
                                            String traceId,
                                            long latencyMs) {

    public static CareerFileTranscriptionResult succeeded(String provider,
                                                          String text,
                                                          String taskId,
                                                          String traceId,
                                                          long latencyMs) {
        return new CareerFileTranscriptionResult(provider, "SUCCEEDED", text, taskId, traceId, latencyMs);
    }
}
