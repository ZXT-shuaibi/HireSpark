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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerWebSocketConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(CareerAudioTranscriptionWebSocketHandler.class,
                    () -> mock(CareerAudioTranscriptionWebSocketHandler.class))
            .withBean(CareerTranscriptionHandshakeInterceptor.class,
                    () -> mock(CareerTranscriptionHandshakeInterceptor.class))
            .withUserConfiguration(CareerWebSocketConfig.class);

    @Test
    void voiceInterviewMissingPropertyDoesNotRegisterWebSocketConfig() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(CareerWebSocketConfig.class));
    }

    @Test
    void voiceInterviewDisabledDoesNotRegisterWebSocketConfig() {
        contextRunner.withPropertyValues("career.features.voice-interview=false")
                .run(context -> assertThat(context).doesNotHaveBean(CareerWebSocketConfig.class));
    }

    @Test
    void voiceInterviewEnabledRegistersWebSocketConfig() {
        contextRunner.withPropertyValues("career.features.voice-interview=true")
                .run(context -> assertThat(context).hasSingleBean(CareerWebSocketConfig.class));
    }

    @Test
    void defaultAllowedOriginPatternsAreRestrictedToLocalDevelopment() {
        CareerAudioTranscriptionWebSocketHandler handler = mock(CareerAudioTranscriptionWebSocketHandler.class);
        CareerTranscriptionHandshakeInterceptor interceptor = mock(CareerTranscriptionHandshakeInterceptor.class);
        CareerWebSocketConfig config = new CareerWebSocketConfig(handler, interceptor);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(eq(handler), eq("/career/interviews/{sessionId}/transcription/ws"))).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
