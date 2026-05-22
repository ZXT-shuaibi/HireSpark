package com.nageoffer.ai.ragent.conversation.port;

public record ConversationMessageView(String id,
                                      String conversationId,
                                      String userId,
                                      String role,
                                      String content,
                                      Integer sequence,
                                      String traceRef) {
}
