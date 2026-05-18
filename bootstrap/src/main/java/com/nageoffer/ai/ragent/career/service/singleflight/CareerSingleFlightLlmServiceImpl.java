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

package com.nageoffer.ai.ragent.career.service.singleflight;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerTaskAttemptDO;
import com.nageoffer.ai.ragent.career.service.attempt.CareerTaskAttemptRecorder;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CareerSingleFlightLlmServiceImpl implements CareerSingleFlightLlmService {

    private static final int KEY_MAX_LENGTH = 200;
    private static final int RESULT_MAX_LENGTH = 200_000;

    private final CareerSingleFlightService singleFlightService;
    private final LLMService llmService;
    private final CareerTaskAttemptRecorder attemptRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String chat(String scene, String singleFlightKey, String traceId, ChatRequest request) {
        String key = stableKey(scene, singleFlightKey);
        Optional<CareerSingleFlightRecordDO> replay = singleFlightService.replayIfAvailable(key);
        if (replay.isPresent()) {
            attemptRecorder.replayed(scene, singleFlightKey, key, traceId, request);
            return unwrapResult(replay.get().getResultJson());
        }

        String ownerId = UUID.randomUUID().toString();
        CareerSingleFlightService.AcquireResult acquireResult =
                singleFlightService.tryAcquire(scene, key, ownerId, traceId);
        CareerSingleFlightRecordDO record = acquireResult.record();
        if (acquireResult.replayAvailable() && record != null) {
            attemptRecorder.replayed(scene, singleFlightKey, key, traceId, request);
            return unwrapResult(record.getResultJson());
        }
        if (!acquireResult.owner()) {
            Optional<CareerSingleFlightRecordDO> available = singleFlightService.replayIfAvailable(key);
            if (available.isPresent()) {
                attemptRecorder.replayed(scene, singleFlightKey, key, traceId, request);
                return unwrapResult(available.get().getResultJson());
            }
            CareerTaskAttemptDO attempt = attemptRecorder.start(scene, singleFlightKey, key, traceId, request);
            ServiceException ex = new ServiceException("AI request is already running for the same input");
            attemptRecorder.failed(attempt, ex, 0L);
            throw ex;
        }

        long fencingToken = record == null || record.getFencingToken() == null ? 0L : record.getFencingToken();
        CareerTaskAttemptDO attempt = attemptRecorder.start(scene, singleFlightKey, key, traceId, request);
        long startTime = System.currentTimeMillis();
        try {
            singleFlightService.heartbeat(key, ownerId, fencingToken);
            String result = llmService.chat(request);
            singleFlightService.completeSuccess(key, ownerId, fencingToken, wrapResult(result));
            attemptRecorder.success(attempt, System.currentTimeMillis() - startTime);
            return result;
        } catch (RuntimeException ex) {
            singleFlightService.completeFailure(key, ownerId, fencingToken, errorType(ex));
            attemptRecorder.failed(attempt, ex, System.currentTimeMillis() - startTime);
            throw ex;
        }
    }

    private String stableKey(String scene, String rawKey) {
        String normalizedScene = StrUtil.blankToDefault(scene, "CAREER_AI").trim();
        String normalizedKey = StrUtil.blankToDefault(rawKey, "").trim();
        String digest = sha256(normalizedScene + ":" + normalizedKey);
        String prefix = normalizedScene + ":";
        if (prefix.length() + digest.length() > KEY_MAX_LENGTH) {
            return digest;
        }
        return prefix + digest;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException("Failed to build AI single-flight key");
        }
    }

    private String limitResult(String result) {
        if (result == null || result.length() <= RESULT_MAX_LENGTH) {
            return result;
        }
        return result.substring(0, RESULT_MAX_LENGTH);
    }

    private String wrapResult(String result) {
        try {
            return objectMapper.writeValueAsString(Map.of("response", limitResult(result)));
        } catch (Exception ex) {
            throw new ServiceException("Failed to serialize AI single-flight result");
        }
    }

    private String unwrapResult(String resultJson) {
        if (StrUtil.isBlank(resultJson)) {
            return resultJson;
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode response = root.get("response");
            return response == null || response.isNull() ? resultJson : response.asText();
        } catch (Exception ex) {
            return resultJson;
        }
    }

    private String errorType(RuntimeException ex) {
        String name = ex.getClass().getSimpleName();
        return StrUtil.blankToDefault(name, "AI_CALL_FAILED");
    }
}
