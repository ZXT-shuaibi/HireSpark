package com.nageoffer.ai.ragent.rag.config;

import com.nageoffer.ai.ragent.rag.websocket.RagChatWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagWebSocketConfigTest {

    @Test
    void registersBidirectionalRagChatWebSocketEndpoint() {
        RagChatWebSocketHandler handler = mock(RagChatWebSocketHandler.class);
        RagWebSocketConfig config = new RagWebSocketConfig(handler, new String[]{"http://localhost:*"});
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/rag/v3/chat/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("http://localhost:*")).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(handler, "/rag/v3/chat/ws");
        verify(registration).setAllowedOriginPatterns("http://localhost:*");
    }
}
