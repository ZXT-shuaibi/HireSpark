package com.nageoffer.ai.ragent.career.service.scoring;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class InterviewRuleBasedScorer {

    private static final int SHORT_ANSWER_CHAR_THRESHOLD = 12;
    private static final int SHORT_ANSWER_SCORE_CAP = 55;
    private static final int MISSING_POINT_PENALTY = 5;

    public InterviewRuleBasedScore score(Integer modelScore,
                                         String question,
                                         String answer,
                                         Map<String, Object> feedback) {
        int score = clamp(modelScore == null ? 0 : modelScore);
        List<String> matchedRules = new ArrayList<>();
        List<String> explanations = new ArrayList<>();
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("modelScore", score);
        dimensions.put("answerEvidence", evidence(question, answer));

        if (answerLength(answer) < SHORT_ANSWER_CHAR_THRESHOLD) {
            score = Math.min(score, SHORT_ANSWER_SCORE_CAP);
            matchedRules.add("SHORT_ANSWER");
            explanations.add("答案过短，无法支撑高分评价");
        }

        List<?> missingPoints = readList(feedback == null ? null : feedback.get("missingPoints"));
        if (!missingPoints.isEmpty()) {
            int penalty = missingPoints.size() * MISSING_POINT_PENALTY;
            score = Math.max(0, score - penalty);
            matchedRules.add("MISSING_POINTS");
            explanations.add("存在关键缺失点：" + missingPoints);
            dimensions.put("missingPointPenalty", penalty);
        } else {
            dimensions.put("missingPointPenalty", 0);
        }

        return new InterviewRuleBasedScore(score, List.copyOf(matchedRules), List.copyOf(explanations), dimensions);
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private int answerLength(String answer) {
        return StrUtil.blankToDefault(answer, "").trim().length();
    }

    private Map<String, Object> evidence(String question, String answer) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("questionChars", StrUtil.blankToDefault(question, "").trim().length());
        evidence.put("answerChars", answerLength(answer));
        return evidence;
    }

    private List<?> readList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }
}
