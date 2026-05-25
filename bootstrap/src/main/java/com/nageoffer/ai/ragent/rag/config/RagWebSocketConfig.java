package com.nageoffer.ai.ragent.rag.config;

import com.nageoffer.ai.ragent.rag.websocket.RagChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class RagWebSocketConfig implements WebSocketConfigurer {

    private static final String[] DEFAULT_ALLOWED_ORIGIN_PATTERNS = {"http://localhost:*", "http://127.0.0.1:*"};

    private final RagChatWebSocketHandler handler;
    private final String[] allowedOriginPatterns;

    @Autowired
    public RagWebSocketConfig(RagChatWebSocketHandler handler,
                              @Value("${rag.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                              String[] allowedOriginPatterns) {
        this.handler = handler;
        this.allowedOriginPatterns = normalizeAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/rag/v3/chat/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    private String[] normalizeAllowedOriginPatterns(String[] configuredPatterns) {
        if (configuredPatterns == null || configuredPatterns.length == 0) {
            return DEFAULT_ALLOWED_ORIGIN_PATTERNS;
        }
        String[] normalized = Arrays.stream(configuredPatterns)
                .filter(pattern -> pattern != null && !pattern.trim().isEmpty())
                .map(String::trim)
                .toArray(String[]::new);
        return normalized.length == 0 ? DEFAULT_ALLOWED_ORIGIN_PATTERNS : normalized;
    }
}
