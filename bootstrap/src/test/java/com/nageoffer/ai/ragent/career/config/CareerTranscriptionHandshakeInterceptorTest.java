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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CareerTranscriptionHandshakeInterceptorTest {

    @Test
    void extractInterviewSessionIdFromTranscriptionPath() {
        String sessionId = CareerTranscriptionHandshakeInterceptor.extractInterviewSessionId(
                "/career/interviews/session-1/transcription/ws"
        );

        assertEquals("session-1", sessionId);
        assertEquals("", CareerTranscriptionHandshakeInterceptor.extractInterviewSessionId("/career/interviews/session-1"));
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        CareerTranscriptionHandshakeInterceptor interceptor = newInterceptor(mapper, null);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = interceptor.beforeHandshake(request("/career/interviews/session-1/transcription/ws"),
                response, null, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(mapper);
    }

    @Test
    void missingSessionIsRejectedWith403() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        CareerTranscriptionHandshakeInterceptor interceptor = newInterceptor(mapper, "user-1");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = interceptor.beforeHandshake(request("/career/interviews/session-1/transcription/ws"),
                response, null, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void terminalSessionIsRejectedWith403() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(session("session-1", "user-1", InterviewSessionStatus.COMPLETED));
        CareerTranscriptionHandshakeInterceptor interceptor = newInterceptor(mapper, "user-1");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = interceptor.beforeHandshake(request("/career/interviews/session-1/transcription/ws"),
                response, null, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidStatusIsRejectedWith403() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        InterviewSessionDO session = new InterviewSessionDO();
        session.setId("session-1");
        session.setUserId("user-1");
        session.setStatus("UNKNOWN");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(session);
        CareerTranscriptionHandshakeInterceptor interceptor = newInterceptor(mapper, "user-1");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = interceptor.beforeHandshake(request("/career/interviews/session-1/transcription/ws"),
                response, null, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void activeOwnedSessionWritesHandshakeAttributes() {
        InterviewSessionMapper mapper = mock(InterviewSessionMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(session("session-1", "user-1", InterviewSessionStatus.RUNNING));
        CareerTranscriptionHandshakeInterceptor interceptor = newInterceptor(mapper, "user-1");
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(request("/career/interviews/session-1/transcription/ws"),
                response, null, attributes);

        assertTrue(allowed);
        assertEquals("user-1", attributes.get(CareerTranscriptionHandshakeInterceptor.ATTR_USER_ID));
        assertEquals("session-1", attributes.get(CareerTranscriptionHandshakeInterceptor.ATTR_INTERVIEW_SESSION_ID));
    }

    @Test
    void queryTokenExtractionSupportsBrowserWebSocketParameters() {
        CareerTranscriptionHandshakeInterceptor.SaTokenLoginIdResolver resolver =
                new CareerTranscriptionHandshakeInterceptor.SaTokenLoginIdResolver();

        assertEquals("Bearer abc", resolver.extractToken(request("/career/interviews/session-1/transcription/ws?Authorization=Bearer%20abc")));
        assertEquals("abc", resolver.normalizeToken("Bearer abc"));
        assertEquals("token-1", resolver.extractToken(request("/career/interviews/session-1/transcription/ws?token=token-1")));
        assertEquals("satoken-1", resolver.extractToken(request("/career/interviews/session-1/transcription/ws?satoken=satoken-1")));
        assertNull(resolver.normalizeToken("   "));
    }

    private CareerTranscriptionHandshakeInterceptor newInterceptor(InterviewSessionMapper mapper, String userId) {
        return new CareerTranscriptionHandshakeInterceptor(mapper, request -> userId);
    }

    private ServerHttpRequest request(String pathAndQuery) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost" + pathAndQuery));
        return request;
    }

    private InterviewSessionDO session(String sessionId, String userId, InterviewSessionStatus status) {
        InterviewSessionDO session = new InterviewSessionDO();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus(status.name());
        return session;
    }
}
