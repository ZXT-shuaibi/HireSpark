package com.nageoffer.ai.ragent.career.service.scoring;

import java.util.List;
import java.util.Map;

public record InterviewRuleBasedScore(int finalScore,
                                      List<String> matchedRules,
                                      List<String> explanations,
                                      Map<String, Object> dimensions) {
}
