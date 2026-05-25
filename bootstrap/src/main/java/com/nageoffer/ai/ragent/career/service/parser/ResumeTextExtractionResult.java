package com.nageoffer.ai.ragent.career.service.parser;

public record ResumeTextExtractionResult(String text, String contentSource) {

    public static final String SOURCE_TIKA = "TIKA";
    public static final String SOURCE_OCR_ENHANCED = "OCR_ENHANCED";
}
