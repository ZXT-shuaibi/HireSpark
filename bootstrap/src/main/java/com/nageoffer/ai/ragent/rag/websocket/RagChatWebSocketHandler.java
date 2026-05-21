package com.nageoffer.ai.ragent.rag.websocket;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagChatWebSocketHandler extends TextWebSocketHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RAGChatService ragChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        send(session, "connected", Map.of("sessionId", session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), MAP_TYPE);
        } catch (IOException ex) {
            send(session, "error", Map.of("message", "消息不是合法 JSON"));
            return;
        }
        String type = stringValue(payload.get("type"));
        switch (type) {
            case "ping" -> send(session, "pong", Map.of("time", System.currentTimeMillis()));
            case "chat" -> startChat(session, payload);
            case "stop" -> stopChat(session, payload);
            default -> send(session, "error", Map.of("message", "未知指令：" + type));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("RAG WebSocket transport error, sessionId={}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void startChat(WebSocketSession session, Map<String, Object> payload) throws IOException {
        String question = stringValue(payload.get("question"));
        if (StrUtil.isBlank(question)) {
            send(session, "error", Map.of("message", "question 不能为空"));
            return;
        }
        String conversationId = stringValue(payload.get("conversationId"));
        Boolean deepThinking = booleanValue(payload.get("deepThinking"));
        RagWebSocketSseEmitter emitter = new RagWebSocketSseEmitter(session, objectMapper);
        ragChatService.streamChat(question, conversationId, deepThinking, emitter);
    }

    private void stopChat(WebSocketSession session, Map<String, Object> payload) throws IOException {
        String taskId = stringValue(payload.get("taskId"));
        if (StrUtil.isBlank(taskId)) {
            send(session, "error", Map.of("message", "taskId 不能为空"));
            return;
        }
        ragChatService.stopTask(taskId);
        send(session, "stop_ack", Map.of("taskId", taskId));
    }

    private void send(WebSocketSession session, String type, Object data) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("data", data);
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? Boolean.FALSE : Boolean.parseBoolean(String.valueOf(value));
    }
}
