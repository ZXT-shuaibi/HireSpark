package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.scoring.InterviewRuleBasedScore;
import com.nageoffer.ai.ragent.career.service.scoring.InterviewRuleBasedScorer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewRuleBasedScorerTest {

    private final InterviewRuleBasedScorer scorer = new InterviewRuleBasedScorer();

    @Test
    void capsShortAnswerEvenWhenModelScoreIsHigh() {
        InterviewRuleBasedScore score = scorer.score(90, "什么是索引", "会。", Map.of());

        assertThat(score.finalScore()).isLessThanOrEqualTo(55);
        assertThat(score.matchedRules()).contains("SHORT_ANSWER");
        assertThat(score.explanations()).anyMatch(item -> item.contains("答案过短"));
    }

    @Test
    void penalizesMissingPointsAndKeepsTraceableDimensions() {
        InterviewRuleBasedScore score = scorer.score(82,
                "请讲一次线上问题排查",
                "我先看日志，再定位 SQL 慢查询，最后加索引并回滚风险变更。",
                Map.of("missingPoints", List.of("量化指标", "复盘沉淀")));

        assertThat(score.finalScore()).isEqualTo(72);
        assertThat(score.matchedRules()).contains("MISSING_POINTS");
        assertThat(score.dimensions()).containsKeys("modelScore", "missingPointPenalty", "answerEvidence");
    }
}
