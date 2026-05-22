package com.nageoffer.ai.ragent.career.service.xunfei;

import java.util.List;

public record XunfeiNlpResult(String provider,
                              String sid,
                              List<String> keywords,
                              List<String> entities,
                              String sentiment,
                              String rawJson) {
}
