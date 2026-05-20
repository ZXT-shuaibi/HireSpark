package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryImportanceScorerTest {

    private final ConversationMemoryImportanceScorer scorer = new ConversationMemoryImportanceScorer();

    @Test
    void scoresBlankContentAsZero() {
        assertThat(scorer.score(message("1", "user", "  "))).isZero();
        assertThat(scorer.score(null)).isZero();
    }

    @Test
    void scoresUserPreferenceConstraintsEvidenceAndNumbersAsImportant() {
        ConversationMessageDO message = message("2", "user",
                "preference: remote first; constraint: budget 30 minutes; evidence from production log; risk score 85");

        assertThat(scorer.score(message)).isGreaterThanOrEqualTo(70);
        assertThat(scorer.hasProtectedSignal(message)).isTrue();
        assertThat(scorer.hasRiskSignal(message)).isTrue();
        assertThat(scorer.hasKeyEvidenceSignal(message)).isTrue();
    }

    @Test
    void scoresOrdinaryAssistantExplanationLowerThanImportantUserFact() {
        ConversationMessageDO assistant = message("3", "assistant", "This can be explained in three simple steps.");
        ConversationMessageDO userFact = message("4", "user",
                "fact: candidate completed first interview answer; time budget is 20 minutes.");

        assertThat(scorer.score(assistant)).isLessThan(70);
        assertThat(scorer.score(userFact)).isGreaterThan(scorer.score(assistant));
        assertThat(scorer.hasLongTermFactSignal(userFact)).isTrue();
    }

    private ConversationMessageDO message(String id, String role, String content) {
        return ConversationMessageDO.builder()
                .id(id)
                .role(role)
                .content(content)
                .build();
    }
}
