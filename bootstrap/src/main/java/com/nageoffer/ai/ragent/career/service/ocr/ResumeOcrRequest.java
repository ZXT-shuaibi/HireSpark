package com.nageoffer.ai.ragent.career.service.ocr;

public record ResumeOcrRequest(byte[] imageBytes,
                               String imageFormat,
                               String traceId,
                               String sourceName,
                               int pageNumber) {
}
