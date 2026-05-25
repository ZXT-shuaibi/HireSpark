package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryCompressionPlannerTest {

    private final ConversationMemoryCompressionPlanner planner =
            new ConversationMemoryCompressionPlanner(new ConversationMemoryImportanceScorer());

    @Test
    void keepsHotContextAndSplitsOlderMessagesIntoFiveBuckets() {
        MemoryProperties properties = new MemoryProperties();
        properties.setRecentKeepMessages(2);
        properties.setImportantMessageThreshold(70);
        properties.setSummaryMaxSourceMessages(2);

        List<ConversationMessageDO> messages = List.of(
                message("1", "user", "ordinary greeting"),
                message("2", "user", "fact: candidate has 5 years Java experience and prefers remote work"),
                message("3", "assistant", "ordinary explanation A"),
                message("4", "assistant", "evidence: score 82 came from interview rubric"),
                message("5", "user", "recent message 1"),
                message("6", "assistant", "recent message 2")
        );

        ConversationMemoryCompressionPlan plan = planner.plan(messages, properties, "trace-1");

        assertThat(ids(plan.getHotContextMessages())).containsExactly("5", "6");
        assertThat(ids(plan.getLongTermFactMessages())).containsExactly("2");
        assertThat(ids(plan.getKeyEvidenceMessages())).containsExactly("4");
        assertThat(ids(plan.getRiskFlagMessages())).isEmpty();
        assertThat(ids(plan.getShortSummaryMessages())).containsExactly("1", "3");
        assertThat(plan.getSourceMessageIds()).containsExactly("1", "3");
        assertThat(plan.getProtectedMessageIds()).containsExactly("2", "4");
        assertThat(plan.getSourceTurnRefs()).containsExactly("turn#1:1", "turn#3:3");
        assertThat(plan.getProtectedTurnRefs()).containsExactly("turn#2:2", "turn#4:4");
        assertThat(plan.getTraceId()).isEqualTo("trace-1");
    }

    @Test
    void treatsAllMessagesAsHotContextWhenMessageCountDoesNotExceedRecentKeepLimit() {
        MemoryProperties properties = new MemoryProperties();
        properties.setRecentKeepMessages(3);

        List<ConversationMessageDO> messages = List.of(
                message("1", "user", "fact: important"),
                message("2", "assistant", "explanation")
        );

        ConversationMemoryCompressionPlan plan = planner.plan(messages, properties);

        assertThat(ids(plan.getHotContextMessages())).containsExactly("1", "2");
        assertThat(plan.getProtectedMessages()).isEmpty();
        assertThat(plan.getShortSummaryMessages()).isEmpty();
        assertThat(plan.getSourceMessageIds()).isEmpty();
    }

    @Test
    void classifiesOlderRiskSignalsAsRiskFlagsEvenWhenScoreThresholdIsHigh() {
        MemoryProperties properties = new MemoryProperties();
        properties.setRecentKeepMessages(1);
        properties.setImportantMessageThreshold(95);

        List<ConversationMessageDO> messages = List.of(
                message("1", "assistant", "risk: unsupported claim needs confirmation"),
                message("2", "user", "recent message")
        );

        ConversationMemoryCompressionPlan plan = planner.plan(messages, properties);

        assertThat(ids(plan.getRiskFlagMessages())).containsExactly("1");
        assertThat(plan.getProtectedMessageIds()).containsExactly("1");
        assertThat(plan.getShortSummaryMessages()).isEmpty();
    }

    private List<String> ids(List<ConversationMessageDO> messages) {
        return messages.stream().map(ConversationMessageDO::getId).toList();
    }

    private ConversationMessageDO message(String id, String role, String content) {
        return ConversationMessageDO.builder()
                .id(id)
                .role(role)
                .content(content)
                .build();
    }
}
