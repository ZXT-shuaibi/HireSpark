package com.nageoffer.ai.ragent.rag.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class RagWebSocketSseEmitter extends SseEmitter {

    private final WebSocketSession session;
    private final ObjectMapper objectMapper;

    public RagWebSocketSseEmitter(WebSocketSession session) {
        this(session, new ObjectMapper());
    }

    RagWebSocketSseEmitter(WebSocketSession session, ObjectMapper objectMapper) {
        super(0L);
        this.session = session;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public synchronized void send(Object object) throws IOException {
        sendWebSocketEvent("message", object);
    }

    @Override
    public synchronized void send(Object object, org.springframework.http.MediaType mediaType) throws IOException {
        send(object);
    }

    @Override
    public synchronized void send(SseEventBuilder builder) throws IOException {
        String eventName = "message";
        Set<ResponseBodyEmitter.DataWithMediaType> items = builder.build();
        for (ResponseBodyEmitter.DataWithMediaType item : items) {
            Object data = item.getData();
            if (data instanceof String text) {
                String parsedEvent = parseEventName(text);
                if (parsedEvent != null) {
                    eventName = parsedEvent;
                    continue;
                }
                String parsedData = parseData(text);
                if (parsedData != null) {
                    sendWebSocketEvent(eventName, parsedData);
                }
                continue;
            }
            sendWebSocketEvent(eventName, data);
        }
    }

    @Override
    public void complete() {
        try {
            sendWebSocketEvent("complete", "[COMPLETE]");
        } catch (IOException ignored) {
        }
    }

    @Override
    public void completeWithError(Throwable ex) {
        try {
            sendWebSocketEvent("error", ex == null ? "unknown" : ex.getMessage());
        } catch (IOException ignored) {
        }
    }

    private void sendWebSocketEvent(String type, Object data) throws IOException {
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

    private String parseEventName(String text) {
        if (text == null || !text.startsWith("event:")) {
            return null;
        }
        return text.substring("event:".length()).trim();
    }

    private String parseData(String text) {
        if (text == null || !text.startsWith("data:")) {
            return null;
        }
        return text.substring("data:".length()).trim();
    }
}
