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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerAudioTranscriptionWebSocketHandlerTest {

    @Test
    void startWithoutAsrProviderReturnsClearError() throws Exception {
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(null);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));

        assertTrue(messages.get(0).contains("\"type\":\"error\""));
        assertTrue(messages.get(0).contains("实时 ASR 服务未配置"));
    }

    @Test
    void binaryBeforeStartReturnsStartRequiredError() throws Exception {
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(null);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleBinaryMessage(session, new BinaryMessage("audio".getBytes(StandardCharsets.UTF_8)));

        assertTrue(messages.get(0).contains("\"type\":\"error\""));
        assertTrue(messages.get(0).contains("start_transcription"));
    }

    @Test
    void invalidControlJsonReturnsInvalidCommand() throws Exception {
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(null);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{bad json"));

        assertTrue(messages.get(0).contains("\"type\":\"invalid_command\""));
        assertTrue(messages.get(0).contains("合法 JSON"));
    }

    @Test
    void startWithAsrProviderPushesAssembledSnapshot() throws Exception {
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> {
            segmentConsumer.accept(new AstTranscriptionSegment(1, null, null, 0, 1000, "answer", false));
            return new CompletableFuture<>();
        };
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));

        assertTrue(messages.get(0).contains("\"type\":\"transcription_started\""));
        assertTrue(messages.get(1).contains("\"type\":\"transcription\""));
        assertTrue(messages.get(1).contains("\"fullText\":\"answer\""));
        assertTrue(messages.get(1).contains("\"updateAction\":\"replace\""));
    }

    @Test
    void providerStartFailureReturnsClearError() throws Exception {
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> {
            throw new IllegalStateException("provider down");
        };
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));

        assertTrue(messages.get(0).contains("\"type\":\"error\""));
        assertTrue(messages.get(0).contains("provider down"));
    }

    @Test
    void startAckFailureCancelsProviderFuture() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> future;
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        WebSocketSession session = newThrowingSession();

        assertThrows(IOException.class, () ->
                handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}")));

        assertTrue(future.isCancelled());
    }

    @Test
    void stopCancelsProviderFutureAndClosesSession() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> future;
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"stop_transcription\"}"));

        assertTrue(future.isCancelled());
        assertTrue(messages.get(messages.size() - 1).contains("\"type\":\"transcription_stopped\""));
    }

    @Test
    void closeCancelsProviderFutureAndRejectsLaterAudio() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> future;
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));
        handler.afterConnectionClosed(session, null);
        handler.handleBinaryMessage(session, new BinaryMessage("audio".getBytes(StandardCharsets.UTF_8)));

        assertTrue(future.isCancelled());
        assertTrue(messages.get(messages.size() - 1).contains("\"type\":\"error\""));
        assertTrue(messages.get(messages.size() - 1).contains("start_transcription"));
    }

    @Test
    void transportErrorCancelsProviderFutureAndRejectsLaterAudio() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> future;
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));
        handler.handleTransportError(session, new IllegalStateException("network broken"));
        handler.handleBinaryMessage(session, new BinaryMessage("audio".getBytes(StandardCharsets.UTF_8)));

        assertTrue(future.isCancelled());
        assertTrue(messages.stream().anyMatch(message -> message.contains("network broken")));
        assertTrue(messages.get(messages.size() - 1).contains("\"type\":\"error\""));
        assertTrue(messages.get(messages.size() - 1).contains("start_transcription"));
    }

    @Test
    void repeatedStartReturnsAlreadyStarted() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> future;
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));

        assertTrue(messages.get(messages.size() - 1).contains("\"type\":\"transcription_already_started\""));
        future.cancel(true);
    }

    @Test
    void finalCallbackDoesNotDuplicateExistingSegments() throws Exception {
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> {
            segmentConsumer.accept(new AstTranscriptionSegment(1, "apd", null, null, null, "hello ", false));
            segmentConsumer.accept(new AstTranscriptionSegment(2, "apd", null, null, null, "world", false));
            return CompletableFuture.completedFuture("hello world");
        };
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));

        String finalMessage = messages.get(messages.size() - 1);
        assertTrue(finalMessage.contains("\"type\":\"final\""));
        assertTrue(finalMessage.contains("\"fullText\":\"hello world\""));
        assertTrue(finalMessage.contains("\"committedText\":\"hello world\""));
        assertTrue(finalMessage.contains("\"liveText\":\"\""));
    }

    @Test
    void exceptionalFutureCompletionCleansSessionAndRejectsLaterAudio() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        CareerAudioTranscriptionService service = (audioInputStream, segmentConsumer) -> future;
        CareerAudioTranscriptionWebSocketHandler handler = newHandler(service);
        List<String> messages = new ArrayList<>();
        WebSocketSession session = newSession(messages);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"start_transcription\"}"));
        future.completeExceptionally(new IllegalStateException("asr failed"));
        handler.handleBinaryMessage(session, new BinaryMessage("audio".getBytes(StandardCharsets.UTF_8)));

        assertTrue(messages.stream().anyMatch(message -> message.contains("\"type\":\"error\"")
                && message.contains("asr failed")));
        assertTrue(messages.get(messages.size() - 1).contains("\"type\":\"error\""));
        assertTrue(messages.get(messages.size() - 1).contains("start_transcription"));
    }

    @SuppressWarnings("unchecked")
    private CareerAudioTranscriptionWebSocketHandler newHandler(CareerAudioTranscriptionService service) {
        ObjectProvider<CareerAudioTranscriptionService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return new CareerAudioTranscriptionWebSocketHandler(provider);
    }

    private WebSocketSession newSession(List<String> messages) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");
        attributes.put("interviewSessionId", "session-1");
        when(session.getId()).thenReturn("ws-1");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            messages.add(message.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        return session;
    }

    private WebSocketSession newThrowingSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");
        attributes.put("interviewSessionId", "session-1");
        when(session.getId()).thenReturn("ws-1");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        doThrow(new IOException("socket closed")).when(session).sendMessage(any(TextMessage.class));
        return session;
    }
}
