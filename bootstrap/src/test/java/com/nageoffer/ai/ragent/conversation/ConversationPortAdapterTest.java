package com.nageoffer.ai.ragent.conversation;

import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.conversation.adapter.CareerInterviewConversationAdapter;
import com.nageoffer.ai.ragent.conversation.adapter.RagConversationAdapter;
import com.nageoffer.ai.ragent.conversation.port.ConversationAppendCommand;
import com.nageoffer.ai.ragent.conversation.port.ConversationMessageView;
import com.nageoffer.ai.ragent.conversation.port.ConversationPort;
import com.nageoffer.ai.ragent.conversation.port.ConversationScene;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationPortAdapterTest {

    @Test
    void ragAdapterDelegatesAppendAndLoadToMemoryService() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        when(memoryService.load("c-1", "u-1")).thenReturn(List.of(ChatMessage.user("你好")));
        when(memoryService.append(any(), any(), any())).thenReturn("m-1");
        ConversationPort adapter = new RagConversationAdapter(memoryService);

        List<ConversationMessageView> history = adapter.load("c-1", "u-1");
        String messageId = adapter.append(new ConversationAppendCommand(
                "c-1", "u-1", "ASSISTANT", "你好，我在", null));

        assertThat(history).extracting(ConversationMessageView::content).containsExactly("你好");
        assertThat(messageId).isEqualTo("m-1");
        verify(memoryService).append("c-1", "u-1", ChatMessage.assistant("你好，我在"));
        assertThat(adapter.scene()).isEqualTo(ConversationScene.RAG);
    }

    @Test
    void careerAdapterProjectsInterviewTurnsIntoUnifiedMessagesWithoutWriting() {
        InterviewTurnMapper turnMapper = mock(InterviewTurnMapper.class);
        when(turnMapper.selectList(any())).thenReturn(List.of(
                InterviewTurnDO.builder().id("t-1").turnNo(1).question("介绍索引").answer("索引用于加速查询").build(),
                InterviewTurnDO.builder().id("t-2").turnNo(2).question("如何排查慢 SQL").build()
        ));
        ConversationPort adapter = new CareerInterviewConversationAdapter(turnMapper);

        List<ConversationMessageView> messages = adapter.load("session-1", "u-1");

        assertThat(messages).extracting(ConversationMessageView::role)
                .containsExactly("USER", "ASSISTANT", "USER");
        assertThat(messages).extracting(ConversationMessageView::content)
                .containsExactly("介绍索引", "索引用于加速查询", "如何排查慢 SQL");
        assertThat(adapter.supportsAppend()).isFalse();
        assertThat(adapter.scene()).isEqualTo(ConversationScene.CAREER_INTERVIEW);
    }
}
