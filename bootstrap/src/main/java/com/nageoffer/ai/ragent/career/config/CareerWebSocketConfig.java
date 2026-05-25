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

package com.nageoffer.ai.ragent.career.config;

import com.nageoffer.ai.ragent.career.media.CareerAudioTranscriptionWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * Career WebSocket 配置，注册面试实时转写通道。
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "career.features", name = "voice-interview", havingValue = "true")
public class CareerWebSocketConfig implements WebSocketConfigurer {

    private static final String[] DEFAULT_ALLOWED_ORIGIN_PATTERNS = {"http://localhost:*", "http://127.0.0.1:*"};

    private final CareerAudioTranscriptionWebSocketHandler transcriptionWebSocketHandler;

    private final CareerTranscriptionHandshakeInterceptor transcriptionHandshakeInterceptor;

    private final String[] allowedOriginPatterns;

    /**
     * 创建生产使用的 WebSocket 配置，Origin 白名单默认仅允许本地开发域。
     */
    @Autowired
    public CareerWebSocketConfig(CareerAudioTranscriptionWebSocketHandler transcriptionWebSocketHandler,
                                 CareerTranscriptionHandshakeInterceptor transcriptionHandshakeInterceptor,
                                 @Value("${career.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                                 String[] allowedOriginPatterns) {
        this.transcriptionWebSocketHandler = transcriptionWebSocketHandler;
        this.transcriptionHandshakeInterceptor = transcriptionHandshakeInterceptor;
        this.allowedOriginPatterns = normalizeAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * 创建测试使用的 WebSocket 配置。
     */
    CareerWebSocketConfig(CareerAudioTranscriptionWebSocketHandler transcriptionWebSocketHandler,
                          CareerTranscriptionHandshakeInterceptor transcriptionHandshakeInterceptor) {
        this(transcriptionWebSocketHandler, transcriptionHandshakeInterceptor, DEFAULT_ALLOWED_ORIGIN_PATTERNS);
    }

    /**
     * 注册 Career 面试实时转写 WebSocket 处理器。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(transcriptionWebSocketHandler, "/career/interviews/{sessionId}/transcription/ws")
                .addInterceptors(transcriptionHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * 规整 Origin 白名单，避免空配置退化为全量开放。
     */
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
