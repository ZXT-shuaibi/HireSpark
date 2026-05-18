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
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardService;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CareerSingleFlightLlmServiceImpl implements CareerSingleFlightLlmService {

    private static final int KEY_MAX_LENGTH = 200;
    private static final int RESULT_MAX_LENGTH = 200_000;

    private final CareerSingleFlightService singleFlightService;
    private final LLMService llmService;
    private final CareerTaskAttemptRecorder attemptRecorder;
    private final CareerAiGuardService aiGuardService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行带 single-flight 和 AI Guard 的 Career LLM 调用。
     */
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
            String result = aiGuardService.execute(scene, () -> llmService.chat(request));
            completeOwnerSuccess(key, ownerId, fencingToken, result);
            attemptRecorder.success(attempt, System.currentTimeMillis() - startTime);
            return result;
        } catch (RuntimeException ex) {
            completeOwnerFailure(key, ownerId, fencingToken, ex);
            attemptRecorder.failed(attempt, ex, System.currentTimeMillis() - startTime);
            throw ex;
        }
    }

    /**
     * 完成 owner 成功状态，若 fencing token 已失效则阻止旧 owner 返回不可回放结果。
     */
    private void completeOwnerSuccess(String key, String ownerId, long fencingToken, String result) {
        boolean completed = singleFlightService.completeSuccess(key, ownerId, fencingToken, wrapResult(result));
        if (!completed) {
            throw new ServiceException("AI single-flight owner lost before success completion");
        }
    }

    /**
     * 完成 owner 失败状态，若 owner 已被接管则仅记录日志。
     */
    private void completeOwnerFailure(String key, String ownerId, long fencingToken, RuntimeException ex) {
        boolean completed = singleFlightService.completeFailure(key, ownerId, fencingToken, errorType(ex));
        if (!completed) {
            log.warn("AI single-flight owner lost before failure completion: key={}, errorType={}", key, errorType(ex));
        }
    }

    /**
     * 构建稳定的 single-flight 存储键，避免原始 prompt 过长。
     */
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

    /**
     * 计算输入文本的 SHA-256 摘要。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException("Failed to build AI single-flight key");
        }
    }

    /**
     * 限制模型结果长度，避免 single-flight 回放字段过大。
     */
    private String limitResult(String result) {
        if (result == null || result.length() <= RESULT_MAX_LENGTH) {
            return result;
        }
        return result.substring(0, RESULT_MAX_LENGTH);
    }

    /**
     * 将模型结果包装成可回放 JSON，兼容空响应。
     */
    private String wrapResult(String result) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("response", limitResult(result));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new ServiceException("Failed to serialize AI single-flight result");
        }
    }

    /**
     * 从 single-flight 回放 JSON 中还原模型响应。
     */
    private String unwrapResult(String resultJson) {
        if (StrUtil.isBlank(resultJson)) {
            return resultJson;
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode response = root.get("response");
            if (response == null) {
                return resultJson;
            }
            return response.isNull() ? null : response.asText();
        } catch (Exception ex) {
            return resultJson;
        }
    }

    /**
     * 提取异常类型名称，供 single-flight 失败记录使用。
     */
    private String errorType(RuntimeException ex) {
        String name = ex.getClass().getSimpleName();
        return StrUtil.blankToDefault(name, "AI_CALL_FAILED");
    }
}
