package com.nageoffer.ai.ragent.rag.core.memory;

import java.util.List;

public class ConversationMemoryTriggerDecision {

    private final List<ConversationMemoryTrigger> triggers;
    private final long userTurns;
    private final int estimatedTokens;

    public ConversationMemoryTriggerDecision(List<ConversationMemoryTrigger> triggers,
                                             long userTurns,
                                             int estimatedTokens) {
        this.triggers = triggers == null ? List.of() : List.copyOf(triggers);
        this.userTurns = userTurns;
        this.estimatedTokens = estimatedTokens;
    }

    public boolean shouldCompress() {
        return !triggers.isEmpty();
    }

    public List<ConversationMemoryTrigger> getTriggers() {
        return triggers;
    }

    public long getUserTurns() {
        return userTurns;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }
}
