package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;

public interface ConversationMemorySummaryService {

    void compressIfNeeded(String conversationId, String userId, ChatMessage message);

    default void compressOnStageSwitch(String conversationId, String userId) {
    }

    default void compressOnRecoveryEvent(String conversationId, String userId) {
    }

    ChatMessage loadLatestSummary(String conversationId, String userId);

    ChatMessage decorateIfNeeded(ChatMessage summary);
}
