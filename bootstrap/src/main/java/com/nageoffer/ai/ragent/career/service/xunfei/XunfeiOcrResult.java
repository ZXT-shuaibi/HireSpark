package com.nageoffer.ai.ragent.career.service.xunfei;

import java.util.List;

public record XunfeiOcrResult(String provider, String sid, String text, List<String> lines, String rawJson) {
}
