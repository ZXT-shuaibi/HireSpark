package com.nageoffer.ai.ragent.career.service.ocr;

import java.util.List;

public record ResumeOcrResult(String text,
                              List<String> lines,
                              String provider,
                              String traceId) {
}
