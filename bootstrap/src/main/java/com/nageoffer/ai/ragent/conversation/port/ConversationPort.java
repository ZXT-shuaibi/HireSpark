package com.nageoffer.ai.ragent.conversation.port;

import java.util.List;

public interface ConversationPort {

    ConversationScene scene();

    List<ConversationMessageView> load(String conversationId, String userId);

    default boolean supportsAppend() {
        return true;
    }

    String append(ConversationAppendCommand command);
}
