package com.nageoffer.ai.ragent.career.service.nlp;

import java.util.List;

public record CareerNlpAnalysisResult(String provider,
                                      String sid,
                                      List<String> keywords,
                                      List<String> entities,
                                      String sentiment) {
}
