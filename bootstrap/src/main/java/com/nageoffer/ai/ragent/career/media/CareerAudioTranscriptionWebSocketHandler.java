/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.media;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Career 面试实时转写 WebSocket 处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareerAudioTranscriptionWebSocketHandler extends AbstractWebSocketHandler {

    private static final String ATTR_USER_ID = "userId";

    private static final String ATTR_INTERVIEW_SESSION_ID = "interviewSessionId";

    private final ObjectProvider<CareerAudioTranscriptionService> transcriptionServiceProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentMap<String, TranscriptionSessionHolder> sessions = new ConcurrentHashMap<>();

    /**
     * 建立连接后返回连接确认消息。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sendResponse(session, "connected", "面试实时转写通道已连接", null);
    }

    /**
     * 处理 start_transcription、stop_transcription、ping 等控制消息。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload;
        try {
            payload = parseControlMessage(message.getPayload());
        } catch (IOException ex) {
            sendResponse(session, "invalid_command", "控制消息不是合法 JSON", null);
            return;
        }
        String type = payload.get("type") == null ? "" : String.valueOf(payload.get("type"));
        switch (type) {
            case "ping" -> sendResponse(session, "pong", "pong", System.currentTimeMillis());
            case "start_transcription" -> startTranscription(session);
            case "stop_transcription" -> stopTranscription(session);
            case "get_status" -> sendResponse(session, "status", "面试实时转写通道正常", session.getAttributes().get(ATTR_INTERVIEW_SESSION_ID));
            default -> sendResponse(session, "unknown_command", "未知控制指令：" + type, null);
        }
    }

    /**
     * 处理二进制音频帧并写入会话级缓冲。
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        TranscriptionSessionHolder holder = sessions.get(session.getId());
        if (holder == null || !holder.isActive()) {
            sendResponse(session, "error", "转写会话尚未启动，请先发送 start_transcription", null);
            return;
        }
        holder.context().writeAudio(message.getPayload());
    }

    /**
     * 连接关闭时清理转写上下文。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanupContext(session.getId());
    }

    /**
     * 传输异常时清理转写上下文并尝试回传错误。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        cleanupContext(session.getId());
        sendErrorQuietly(session, "实时转写通道异常：" + exception.getMessage());
    }

    /**
     * 启动实时转写会话。
     */
    private void startTranscription(WebSocketSession session) throws IOException {
        CareerAudioTranscriptionService transcriptionService = transcriptionServiceProvider.getIfAvailable();
        if (transcriptionService == null) {
            sendResponse(session, "error", "实时 ASR 服务未配置", null);
            return;
        }
        TranscriptionSessionHolder existing = sessions.get(session.getId());
        if (existing != null && existing.isActive()) {
            sendResponse(session, "transcription_already_started", "实时转写已经启动", null);
            return;
        }
        if (existing != null) {
            cleanupContext(session.getId());
        }
        TranscriptionSessionContext context = TranscriptionSessionContext.open(
                session.getId(),
                stringAttribute(session, ATTR_INTERVIEW_SESSION_ID),
                stringAttribute(session, ATTR_USER_ID)
        );
        TranscriptionSessionHolder holder = new TranscriptionSessionHolder(context);
        TranscriptionSessionHolder previous = sessions.putIfAbsent(session.getId(), holder);
        if (previous != null) {
            context.close();
            sendResponse(session, "transcription_already_started", "实时转写已经启动", null);
            return;
        }
        AtomicBoolean startAcknowledged = new AtomicBoolean(false);
        Queue<CareerTranscriptionUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
        CompletableFuture<String> future;
        try {
            future = transcriptionService.transcribe(context.getAudioInputStream(), segment -> {
                if (!holder.isCurrent(sessions, session.getId()) || holder.isStopRequested()) {
                    return;
                }
                CareerTranscriptionUpdate update = context.applySegment(segment);
                if (startAcknowledged.get()) {
                    sendUpdate(session, "transcription", "实时转写快照", update);
                    return;
                }
                pendingUpdates.add(update);
            });
        } catch (RuntimeException ex) {
            sessions.remove(session.getId(), holder);
            holder.stop();
            sendResponse(session, "error", "实时 ASR 服务启动失败：" + ex.getMessage(), null);
            return;
        }
        if (future == null || !holder.attachFuture(future)) {
            sessions.remove(session.getId(), holder);
            if (future != null) {
                future.cancel(true);
            }
            holder.stop();
            sendResponse(session, "transcription_already_stopped", "实时转写已经停止", null);
            return;
        }
        try {
            sendResponse(session, "transcription_started", "实时转写已启动", null);
        } catch (IOException ex) {
            sessions.remove(session.getId(), holder);
            holder.stop();
            throw ex;
        }
        startAcknowledged.set(true);
        flushPendingUpdates(session, holder, pendingUpdates);
        future.whenComplete((finalText, throwable) -> handleTranscriptionCompletion(session, holder, finalText, throwable));
    }

    /**
     * 停止实时转写会话。
     */
    private void stopTranscription(WebSocketSession session) throws IOException {
        TranscriptionSessionHolder holder = sessions.remove(session.getId());
        if (holder == null) {
            sendResponse(session, "transcription_already_stopped", "实时转写已经停止", null);
            return;
        }
        holder.stop();
        sendResponse(session, "transcription_stopped", "实时转写已停止", holder.context().getLatestUpdate());
    }

    /**
     * 处理 ASR 服务完成回调。
     */
    private void handleTranscriptionCompletion(WebSocketSession session,
                                               TranscriptionSessionHolder holder,
                                               String finalText,
                                               Throwable throwable) {
        try {
            if (throwable != null && !holder.isStopRequested()) {
                sendErrorQuietly(session, "实时转写失败：" + throwable.getMessage());
                return;
            }
            if (!holder.isStopRequested() && finalText != null && holder.isCurrent(sessions, session.getId())) {
                CareerTranscriptionUpdate update = holder.context().completeFinalText(finalText);
                sendUpdate(session, "final", "实时转写完成，可作为面试答案草稿", update);
            }
        } finally {
            sessions.remove(session.getId(), holder);
            holder.close();
        }
    }

    /**
     * 推送启动前同步产生的转写快照，保证客户端先收到 started 再收到内容快照。
     */
    private void flushPendingUpdates(WebSocketSession session,
                                     TranscriptionSessionHolder holder,
                                     Queue<CareerTranscriptionUpdate> pendingUpdates) {
        CareerTranscriptionUpdate update;
        while ((update = pendingUpdates.poll()) != null) {
            if (!holder.isCurrent(sessions, session.getId()) || holder.isStopRequested()) {
                return;
            }
            sendUpdate(session, "transcription", "实时转写快照", update);
        }
    }

    /**
     * 安静发送错误消息，避免异步完成回调被网络异常打断。
     */
    private void sendErrorQuietly(WebSocketSession session, String message) {
        try {
            sendResponse(session, "error", message, null);
        } catch (IOException ex) {
            log.warn("发送实时转写错误消息失败，sessionId={}", session == null ? null : session.getId(), ex);
        }
    }

    /**
     * 清理指定 WebSocket 会话的转写上下文。
     */
    private void cleanupContext(String websocketSessionId) {
        TranscriptionSessionHolder holder = sessions.remove(websocketSessionId);
        if (holder != null) {
            holder.stop();
        }
    }

    /**
     * 解析控制消息。
     */
    private Map<String, Object> parseControlMessage(String payload) throws IOException {
        if (payload == null || payload.trim().isEmpty()) {
            return Map.of();
        }
        return objectMapper.readValue(payload, new TypeReference<>() {
        });
    }

    /**
     * 获取握手阶段写入的字符串属性。
     */
    private String stringAttribute(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 发送普通 WebSocket 响应。
     */
    private void sendResponse(WebSocketSession session, String type, String message, Object data) throws IOException {
        Map<String, Object> response = baseResponse(type, message);
        response.put("data", data);
        sendJson(session, response);
    }

    /**
     * 发送转写快照响应。
     */
    private void sendUpdate(WebSocketSession session, String type, String message, CareerTranscriptionUpdate update) {
        try {
            Map<String, Object> response = baseResponse(type, message);
            response.put("data", update == null ? null : update.fullText());
            response.put("fullText", update == null ? null : update.fullText());
            response.put("displayText", update == null ? null : update.displayText());
            response.put("committedText", update == null ? null : update.committedText());
            response.put("liveText", update == null ? null : update.liveText());
            response.put("revision", update == null ? null : update.revision());
            response.put("resultStatus", update == null ? null : update.resultStatus());
            response.put("isSnapshot", true);
            response.put("updateAction", "final".equals(type) ? "archive" : "replace");
            response.put("segmentId", update == null ? null : update.segmentId());
            response.put("sentenceSeq", update == null ? null : update.segmentId());
            response.put("segmentText", update == null ? null : update.segmentText());
            response.put("pgs", update == null ? null : update.pgs());
            response.put("rg", update == null ? null : update.rg());
            response.put("bg", update == null ? null : update.bg());
            response.put("ed", update == null ? null : update.ed());
            response.put("isFinalPacket", update != null && update.finalPacket());
            sendJson(session, response);
        } catch (IOException ex) {
            log.warn("发送实时转写快照失败，sessionId={}", session.getId(), ex);
        }
    }

    /**
     * 构建基础响应字段。
     */
    private Map<String, Object> baseResponse(String type, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", type);
        response.put("message", message);
        response.put("isSnapshot", false);
        response.put("updateAction", "none");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * 序列化并发送 JSON 消息。
     */
    private void sendJson(WebSocketSession session, Map<String, Object> response) throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        }
    }

    /**
     * 绑定一个 WebSocket 转写会话的上下文和异步 ASR 任务，避免清理时上下文与 future 分离。
     */
    private static final class TranscriptionSessionHolder {

        private final TranscriptionSessionContext context;

        private final AtomicReference<CompletableFuture<String>> futureRef = new AtomicReference<>();

        private final AtomicBoolean stopped = new AtomicBoolean(false);

        /**
         * 创建转写会话持有对象。
         */
        private TranscriptionSessionHolder(TranscriptionSessionContext context) {
            this.context = context;
        }

        /**
         * 返回会话上下文。
         */
        private TranscriptionSessionContext context() {
            return context;
        }

        /**
         * 绑定 ASR future；如果会话已被关闭，立即拒绝并由调用方取消。
         */
        private boolean attachFuture(CompletableFuture<String> future) {
            if (stopped.get()) {
                return false;
            }
            futureRef.set(future);
            if (!stopped.get()) {
                return true;
            }
            CompletableFuture<String> attached = futureRef.getAndSet(null);
            if (attached != null) {
                attached.cancel(true);
            }
            return false;
        }

        /**
         * 判断当前持有对象是否仍是指定 WebSocket 会话的活动对象。
         */
        private boolean isCurrent(ConcurrentMap<String, TranscriptionSessionHolder> sessions, String websocketSessionId) {
            return sessions.get(websocketSessionId) == this;
        }

        /**
         * 判断转写会话是否仍可接收音频。
         */
        private boolean isActive() {
            return !stopped.get() && context.isActive();
        }

        /**
         * 判断是否已请求停止。
         */
        private boolean isStopRequested() {
            return stopped.get() || context.isStopRequested();
        }

        /**
         * 停止转写会话并取消异步任务。
         */
        private void stop() {
            if (stopped.compareAndSet(false, true)) {
                context.requestStop();
            }
            CompletableFuture<String> future = futureRef.getAndSet(null);
            if (future != null) {
                future.cancel(true);
            }
            context.close();
        }

        /**
         * 关闭转写上下文。
         */
        private void close() {
            stopped.set(true);
            futureRef.set(null);
            context.close();
        }
    }
}
