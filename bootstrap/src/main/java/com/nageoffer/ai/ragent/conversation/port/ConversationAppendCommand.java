package com.nageoffer.ai.ragent.conversation.port;

public record ConversationAppendCommand(String conversationId,
                                        String userId,
                                        String role,
                                        String content,
                                        String traceRef) {
}
