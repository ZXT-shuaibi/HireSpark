package com.nageoffer.ai.ragent.conversation.adapter;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.conversation.port.ConversationAppendCommand;
import com.nageoffer.ai.ragent.conversation.port.ConversationMessageView;
import com.nageoffer.ai.ragent.conversation.port.ConversationPort;
import com.nageoffer.ai.ragent.conversation.port.ConversationScene;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RagConversationAdapter implements ConversationPort {

    private final ConversationMemoryService memoryService;

    @Override
    public ConversationScene scene() {
        return ConversationScene.RAG;
    }

    @Override
    public List<ConversationMessageView> load(String conversationId, String userId) {
        List<ChatMessage> messages = memoryService.load(conversationId, userId);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> message != null && StrUtil.isNotBlank(message.getContent()))
                .map(message -> new ConversationMessageView(
                        null,
                        conversationId,
                        userId,
                        message.getRole().name(),
                        message.getContent(),
                        null,
                        null))
                .toList();
    }

    @Override
    public String append(ConversationAppendCommand command) {
        if (command == null) {
            return null;
        }
        ChatMessage message = toChatMessage(command);
        return memoryService.append(command.conversationId(), command.userId(), message);
    }

    private ChatMessage toChatMessage(ConversationAppendCommand command) {
        ChatMessage.Role role = ChatMessage.Role.fromString(command.role());
        if (role == ChatMessage.Role.ASSISTANT) {
            return ChatMessage.assistant(command.content());
        }
        return ChatMessage.user(command.content());
    }
}
