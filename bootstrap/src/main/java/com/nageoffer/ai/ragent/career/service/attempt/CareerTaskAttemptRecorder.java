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

package com.nageoffer.ai.ragent.career.service.attempt;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.CareerTaskAttemptDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerTaskAttemptMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareerTaskAttemptRecorder {

    private static final int KEY_MAX_LENGTH = 512;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;
    private static final String MODEL_ROUTER_NAME = "RagentModelRouter";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REPLAYED = "REPLAYED";

    private final CareerTaskAttemptMapper attemptMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CareerTaskAttemptDO start(String scene,
                                     String idempotencyKey,
                                     String singleFlightKey,
                                     String traceId,
                                     ChatRequest request) {
        AttemptContext context = parseContext(scene, idempotencyKey);
        CareerTaskAttemptDO attempt = CareerTaskAttemptDO.builder()
                .userId(context.userId())
                .businessId(context.businessId())
                .scene(context.scene())
                .idempotencyKey(limit(idempotencyKey, KEY_MAX_LENGTH))
                .singleFlightKey(limit(singleFlightKey, KEY_MAX_LENGTH))
                .traceId(traceId)
                .modelName(MODEL_ROUTER_NAME)
                .promptSummary(promptSummary(request))
                .status(STATUS_RUNNING)
                .replayed(false)
                .latencyMs(0L)
                .build();
        attemptMapper.insert(attempt);
        return attempt;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CareerTaskAttemptDO replayed(String scene,
                                        String idempotencyKey,
                                        String singleFlightKey,
                                        String traceId,
                                        ChatRequest request) {
        CareerTaskAttemptDO attempt = start(scene, idempotencyKey, singleFlightKey, traceId, request);
        attempt.setStatus(STATUS_REPLAYED);
        attempt.setReplayed(true);
        attempt.setLatencyMs(0L);
        attemptMapper.updateById(attempt);
        return attempt;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void success(CareerTaskAttemptDO attempt, long latencyMs) {
        if (attempt == null) {
            return;
        }
        attempt.setStatus(STATUS_SUCCESS);
        attempt.setReplayed(false);
        attempt.setLatencyMs(Math.max(0L, latencyMs));
        attempt.setErrorType(null);
        attempt.setErrorMessage(null);
        attemptMapper.updateById(attempt);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void failed(CareerTaskAttemptDO attempt, RuntimeException ex, long latencyMs) {
        if (attempt == null) {
            return;
        }
        attempt.setStatus(STATUS_FAILED);
        attempt.setReplayed(false);
        attempt.setLatencyMs(Math.max(0L, latencyMs));
        attempt.setErrorType(errorType(ex));
        attempt.setErrorMessage(limit(errorMessage(ex), ERROR_MESSAGE_MAX_LENGTH));
        attemptMapper.updateById(attempt);
    }

    private AttemptContext parseContext(String scene, String idempotencyKey) {
        String normalizedScene = StrUtil.blankToDefault(scene, "CAREER_AI").trim();
        String userId = null;
        String businessId = null;
        if (StrUtil.isNotBlank(idempotencyKey)) {
            String[] parts = idempotencyKey.split(":", 4);
            if (parts.length > 1) {
                userId = blankToNull(parts[1]);
            }
            if (parts.length > 2) {
                businessId = blankToNull(parts[2]);
            }
        }
        return new AttemptContext(normalizedScene, userId, businessId);
    }

    private String promptSummary(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        List<String> contents = request.getMessages().stream()
                .map(ChatMessage::getContent)
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .toList();
        String joined = String.join("\n", contents);
        return "messages=" + contents.size()
                + ", chars=" + joined.length()
                + ", sha256=" + sha256(joined).substring(0, 16)
                + ", temperature=" + (request.getTemperature() == null ? "default" : request.getTemperature());
    }

    private String errorType(RuntimeException ex) {
        return ex == null ? "UNKNOWN" : StrUtil.blankToDefault(ex.getClass().getSimpleName(), "UNKNOWN");
    }

    private String errorMessage(RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        return StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName());
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.blankToDefault(value, "")
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return "hash-unavailable";
        }
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private record AttemptContext(String scene, String userId, String businessId) {
    }
}
