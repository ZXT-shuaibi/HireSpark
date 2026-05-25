package com.nageoffer.ai.ragent.rag.websocket;

import com.nageoffer.ai.ragent.rag.service.RAGChatService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatWebSocketHandlerTest {

    @Test
    void repliesPongForPingCommand() throws Exception {
        RAGChatService service = mock(RAGChatService.class);
        RagChatWebSocketHandler handler = new RagChatWebSocketHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = session(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        assertThat(messages).anyMatch(item -> item.contains("\"type\":\"pong\""));
    }

    @Test
    void delegatesChatCommandToExistingRagStreamService() throws Exception {
        RAGChatService service = mock(RAGChatService.class);
        RagChatWebSocketHandler handler = new RagChatWebSocketHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = session(messages);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"chat","question":"如何做索引优化？","conversationId":"c-1","deepThinking":true}
                """));

        verify(service).streamChat(eq("如何做索引优化？"), eq("c-1"), eq(true), any(RagWebSocketSseEmitter.class));
    }

    @Test
    void delegatesStopCommandToExistingRagCancellation() throws Exception {
        RAGChatService service = mock(RAGChatService.class);
        RagChatWebSocketHandler handler = new RagChatWebSocketHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = session(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"stop\",\"taskId\":\"task-1\"}"));

        verify(service).stopTask("task-1");
        assertThat(messages).anyMatch(item -> item.contains("\"type\":\"stop_ack\""));
    }

    private WebSocketSession session(List<String> messages) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("ws-1");
        org.mockito.Mockito.doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            messages.add(message.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        return session;
    }
}
