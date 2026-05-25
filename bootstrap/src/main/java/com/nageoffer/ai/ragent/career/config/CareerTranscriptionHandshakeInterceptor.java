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

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Career 实时转写握手拦截器，负责登录态和面试会话归属校验。
 */
@Component
public class CareerTranscriptionHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    static final String ATTR_INTERVIEW_SESSION_ID = "interviewSessionId";

    private static final String TRANSCRIPTION_PATH_PREFIX = "/career/interviews/";

    private static final String TRANSCRIPTION_PATH_SUFFIX = "/transcription/ws";

    private final InterviewSessionMapper interviewSessionMapper;

    private final LoginIdResolver loginIdResolver;

    /**
     * 创建生产使用的握手拦截器。
     */
    public CareerTranscriptionHandshakeInterceptor(InterviewSessionMapper interviewSessionMapper) {
        this(interviewSessionMapper, new SaTokenLoginIdResolver());
    }

    /**
     * 创建可注入登录解析器的握手拦截器，便于单元测试覆盖安全边界。
     */
    CareerTranscriptionHandshakeInterceptor(InterviewSessionMapper interviewSessionMapper,
                                            LoginIdResolver loginIdResolver) {
        this.interviewSessionMapper = interviewSessionMapper;
        this.loginIdResolver = loginIdResolver;
    }

    /**
     * 校验登录态、会话归属和会话状态。
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String userId = loginIdResolver.resolve(request);
        String sessionId = extractInterviewSessionId(request.getURI().getPath());
        if (isBlank(userId)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (isBlank(sessionId) || !canUseInterviewSession(sessionId, userId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_INTERVIEW_SESSION_ID, sessionId);
        return true;
    }

    /**
     * 握手完成后无需额外处理。
     */
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    /**
     * 校验面试会话是否属于当前用户且未结束。
     */
    private boolean canUseInterviewSession(String sessionId, String userId) {
        InterviewSessionDO session = interviewSessionMapper.selectOne(
                Wrappers.<InterviewSessionDO>lambdaQuery()
                        .eq(InterviewSessionDO::getId, sessionId)
                        .eq(InterviewSessionDO::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (session == null || isBlank(session.getStatus())) {
            return false;
        }
        try {
            return !InterviewSessionStatus.valueOf(session.getStatus()).terminal();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 从 WebSocket 路径中提取面试会话 ID。
     */
    static String extractInterviewSessionId(String path) {
        if (path == null || !path.contains(TRANSCRIPTION_PATH_PREFIX) || !path.endsWith(TRANSCRIPTION_PATH_SUFFIX)) {
            return "";
        }
        int start = path.indexOf(TRANSCRIPTION_PATH_PREFIX) + TRANSCRIPTION_PATH_PREFIX.length();
        int end = path.lastIndexOf(TRANSCRIPTION_PATH_SUFFIX);
        if (start >= end) {
            return "";
        }
        return path.substring(start, end);
    }

    /**
     * 获取第一个非空查询参数。
     */
    static String queryParam(ServerHttpRequest request, String name) {
        String value = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 返回第一个非空字符串。
     */
    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断字符串是否为空白。
     */
    static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 登录用户解析器。
     */
    interface LoginIdResolver {

        /**
         * 从握手请求中解析登录用户 ID。
         */
        String resolve(ServerHttpRequest request);
    }

    /**
     * Sa-Token 登录解析器，兼容浏览器 WebSocket 查询参数传 token 的场景。
     */
    static class SaTokenLoginIdResolver implements LoginIdResolver {

        /**
         * 解析当前登录用户。
         */
        @Override
        public String resolve(ServerHttpRequest request) {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                return String.valueOf(loginId);
            }
            String token = normalizeToken(extractToken(request));
            if (isBlank(token)) {
                return null;
            }
            try {
                Object tokenLoginId = StpUtil.getLoginIdByToken(token);
                return tokenLoginId == null ? null : String.valueOf(tokenLoginId);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        /**
         * 从查询参数中提取 WebSocket token。
         */
        String extractToken(ServerHttpRequest request) {
            return firstNonBlank(
                    queryParam(request, "Authorization"),
                    queryParam(request, "token"),
                    queryParam(request, "satoken")
            );
        }

        /**
         * 兼容 Bearer token 写法。
         */
        String normalizeToken(String token) {
            if (isBlank(token)) {
                return null;
            }
            String value = token.trim();
            if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                return value.substring("Bearer ".length()).trim();
            }
            return value;
        }
    }
}
