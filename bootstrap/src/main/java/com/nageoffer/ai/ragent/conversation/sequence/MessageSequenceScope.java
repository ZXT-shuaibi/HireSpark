package com.nageoffer.ai.ragent.conversation.sequence;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.conversation.port.ConversationScene;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

public record MessageSequenceScope(ConversationScene scene,
                                   String conversationId,
                                   String userId) {

    public MessageSequenceScope {
        if (scene == null) {
            throw new ClientException("Conversation scene is required");
        }
        if (StrUtil.isBlank(conversationId)) {
            throw new ClientException("Conversation id is required");
        }
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User id is required");
        }
    }
}
