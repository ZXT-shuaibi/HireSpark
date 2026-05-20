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
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentExecutionTraceDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerTaskAttemptDO;
import com.nageoffer.ai.ragent.career.service.attempt.CareerTaskAttemptRecorder;
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardService;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentTraceCommand;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentTraceService;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class CareerSingleFlightLlmServiceImpl implements CareerSingleFlightLlmService {

    private static final int KEY_MAX_LENGTH = 200;
    private static final int RESULT_MAX_LENGTH = 200_000;

    private final CareerSingleFlightService singleFlightService;
    private final LLMService llmService;
    private final CareerTaskAttemptRecorder attemptRecorder;
    private final CareerAiGuardService aiGuardService;
    private final CareerSingleFlightProperties singleFlightProperties;
    private final CareerSingleFlightLocalReplayCache localReplayCache;
    private final CareerSingleFlightHeartbeatManager heartbeatManager;
    private final CareerAgentTraceService careerAgentTraceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建默认 single-flight LLM 包装器，沿用默认等待参数。
     */
    public CareerSingleFlightLlmServiceImpl(CareerSingleFlightService singleFlightService,
                                            LLMService llmService,
                                            CareerTaskAttemptRecorder attemptRecorder,
                                            CareerAiGuardService aiGuardService) {
        this(singleFlightService,
                llmService,
                attemptRecorder,
                aiGuardService,
                new CareerSingleFlightProperties(),
                new CareerSingleFlightLocalReplayCache(),
                new CareerSingleFlightHeartbeatManager(),
                null);
    }

    /**
     * 创建可自定义等待参数的 single-flight LLM 包装器。
     */
    public CareerSingleFlightLlmServiceImpl(CareerSingleFlightService singleFlightService,
                                            LLMService llmService,
                                            CareerTaskAttemptRecorder attemptRecorder,
                                            CareerAiGuardService aiGuardService,
                                            CareerSingleFlightProperties singleFlightProperties) {
        this(singleFlightService,
                llmService,
                attemptRecorder,
                aiGuardService,
                singleFlightProperties,
                new CareerSingleFlightLocalReplayCache(),
                new CareerSingleFlightHeartbeatManager(),
                null);
    }

    /**
     * 创建带本地 L1 回放和持续心跳续租的 single-flight LLM 包装器。
     */
    public CareerSingleFlightLlmServiceImpl(CareerSingleFlightService singleFlightService,
                                            LLMService llmService,
                                            CareerTaskAttemptRecorder attemptRecorder,
                                            CareerAiGuardService aiGuardService,
                                            CareerSingleFlightProperties singleFlightProperties,
                                            CareerSingleFlightLocalReplayCache localReplayCache,
                                            CareerSingleFlightHeartbeatManager heartbeatManager) {
        this(singleFlightService,
                llmService,
                attemptRecorder,
                aiGuardService,
                singleFlightProperties,
                localReplayCache,
                heartbeatManager,
                null);
    }

    /**
     * 创建带本地 L1 回放、持续心跳续租和 Agent 观测的 single-flight LLM 包装器。
     */
    @Autowired
    public CareerSingleFlightLlmServiceImpl(CareerSingleFlightService singleFlightService,
                                            LLMService llmService,
                                            CareerTaskAttemptRecorder attemptRecorder,
                                            CareerAiGuardService aiGuardService,
                                            CareerSingleFlightProperties singleFlightProperties,
                                            CareerSingleFlightLocalReplayCache localReplayCache,
                                            CareerSingleFlightHeartbeatManager heartbeatManager,
                                            CareerAgentTraceService careerAgentTraceService) {
        this.singleFlightService = singleFlightService;
        this.llmService = llmService;
        this.attemptRecorder = attemptRecorder;
        this.aiGuardService = aiGuardService;
        this.singleFlightProperties = singleFlightProperties == null ? new CareerSingleFlightProperties() : singleFlightProperties;
        this.localReplayCache = localReplayCache == null ? new CareerSingleFlightLocalReplayCache() : localReplayCache;
        this.heartbeatManager = heartbeatManager == null ? new CareerSingleFlightHeartbeatManager() : heartbeatManager;
        this.careerAgentTraceService = careerAgentTraceService;
    }

    /**
     * 执行带 single-flight 和 AI Guard 的 Career LLM 调用。
     */
    @Override
    public String chat(String scene, String singleFlightKey, String traceId, ChatRequest request) {
        String key = stableKey(scene, singleFlightKey);
        CareerAgentExecutionTraceDO agentTrace = startAgentTrace(scene, singleFlightKey, traceId, request);
        Optional<CareerSingleFlightRecordDO> replay = findReplay(key);
        if (replay.isPresent()) {
            attemptRecorder.replayed(scene, singleFlightKey, key, traceId, request);
            String result = unwrapResult(replay.get().getResultJson());
            finishAgentTraceSuccess(agentTrace, result, 0L);
            return result;
        }

        String ownerId = UUID.randomUUID().toString();
        CareerSingleFlightService.AcquireResult acquireResult =
                singleFlightService.tryAcquire(scene, key, ownerId, traceId);
        CareerSingleFlightRecordDO record = acquireResult.record();
        if (acquireResult.replayAvailable() && record != null) {
            cacheReplay(record);
            attemptRecorder.replayed(scene, singleFlightKey, key, traceId, request);
            String result = unwrapResult(record.getResultJson());
            finishAgentTraceSuccess(agentTrace, result, 0L);
            return result;
        }
        if (!acquireResult.owner()) {
            Optional<CareerSingleFlightRecordDO> available = waitForReplay(key);
            if (available.isPresent()) {
                attemptRecorder.replayed(scene, singleFlightKey, key, traceId, request);
                String result = unwrapResult(available.get().getResultJson());
                finishAgentTraceSuccess(agentTrace, result, 0L);
                return result;
            }
            CareerTaskAttemptDO attempt = attemptRecorder.start(scene, singleFlightKey, key, traceId, request);
            ServiceException ex = new ServiceException("AI request is already running for the same input and no replay became available within wait timeout");
            attemptRecorder.failed(attempt, ex, 0L);
            finishAgentTraceFailed(agentTrace, ex, 0L);
            throw ex;
        }

        long fencingToken = record == null || record.getFencingToken() == null ? 0L : record.getFencingToken();
        CareerTaskAttemptDO attempt = attemptRecorder.start(scene, singleFlightKey, key, traceId, request);
        long startTime = System.currentTimeMillis();
        String heartbeatTaskKey = null;
        try {
            ensureOwnerHeartbeat(key, ownerId, fencingToken);
            heartbeatTaskKey = heartbeatManager.start(key,
                    ownerId,
                    fencingToken,
                    singleFlightProperties.heartbeatIntervalMillis(),
                    () -> singleFlightService.heartbeat(key, ownerId, fencingToken));
            String result = aiGuardService.execute(scene, () -> llmService.chat(request));
            completeOwnerSuccess(key, ownerId, fencingToken, result);
            long latencyMs = System.currentTimeMillis() - startTime;
            attemptRecorder.success(attempt, latencyMs);
            finishAgentTraceSuccess(agentTrace, result, latencyMs);
            return result;
        } catch (RuntimeException ex) {
            completeOwnerFailure(key, ownerId, fencingToken, ex);
            long latencyMs = System.currentTimeMillis() - startTime;
            attemptRecorder.failed(attempt, ex, latencyMs);
            finishAgentTraceFailed(agentTrace, ex, latencyMs);
            throw ex;
        } finally {
            heartbeatManager.stop(heartbeatTaskKey);
        }
    }

    /**
     * 按“本地 L1 -> single-flight 服务”的顺序查找成功回放。
     */
    private Optional<CareerSingleFlightRecordDO> findReplay(String key) {
        Optional<CareerSingleFlightRecordDO> localReplay = localReplayCache.get(key);
        if (localReplay.isPresent()) {
            return localReplay;
        }
        Optional<CareerSingleFlightRecordDO> replay = singleFlightService.replayIfAvailable(key);
        replay.ifPresent(this::cacheReplay);
        return replay;
    }

    /**
     * 开始记录 Agent 调用观测，观测失败不阻断主链路。
     */
    private CareerAgentExecutionTraceDO startAgentTrace(String scene, String singleFlightKey, String traceId, ChatRequest request) {
        if (careerAgentTraceService == null) {
            return null;
        }
        AgentContext context = parseAgentContext(scene, singleFlightKey);
        try {
            return careerAgentTraceService.startExecution(CareerAgentTraceCommand.builder()
                    .agentType(context.scene())
                    .scene(context.businessScene())
                    .sessionId(context.businessId())
                    .userId(context.userId())
                    .traceId(traceId)
                    .modelName("RagentModelRouter")
                    .input(request)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Career Agent 调用观测启动失败，忽略观测写入：scene={}, traceId={}", scene, traceId, ex);
            return null;
        }
    }

    /**
     * 标记 Agent 调用成功，观测失败不阻断主链路。
     */
    private void finishAgentTraceSuccess(CareerAgentExecutionTraceDO trace, String result, long latencyMs) {
        if (careerAgentTraceService == null || trace == null) {
            return;
        }
        try {
            careerAgentTraceService.finishSuccess(trace, result, latencyMs);
        } catch (RuntimeException ex) {
            log.warn("Career Agent 调用成功观测写入失败，忽略观测写入：traceId={}", trace.getTraceId(), ex);
        }
    }

    /**
     * 标记 Agent 调用失败，观测失败不阻断主链路。
     */
    private void finishAgentTraceFailed(CareerAgentExecutionTraceDO trace, RuntimeException failure, long latencyMs) {
        if (careerAgentTraceService == null || trace == null) {
            return;
        }
        try {
            careerAgentTraceService.finishFailed(trace, failure, latencyMs);
        } catch (RuntimeException ex) {
            log.warn("Career Agent 调用失败观测写入失败，忽略观测写入：traceId={}", trace.getTraceId(), ex);
        }
    }

    /**
     * 从 single-flight 原始键中解析用户和业务对象。
     */
    private AgentContext parseAgentContext(String scene, String singleFlightKey) {
        String normalizedScene = StrUtil.blankToDefault(scene, "CAREER_AI").trim();
        String businessScene = normalizedScene.contains("INTERVIEW") ? "INTERVIEW"
                : normalizedScene.contains("OPTIMIZATION") ? "OPTIMIZATION"
                : normalizedScene.contains("JD") ? "ALIGNMENT"
                : normalizedScene.contains("RESUME") ? "RESUME"
                : "CAREER";
        String userId = null;
        String businessId = null;
        if (StrUtil.isNotBlank(singleFlightKey)) {
            String[] parts = singleFlightKey.split(":", 4);
            if (parts.length > 1) {
                userId = blankToNull(parts[1]);
            }
            if (parts.length > 2) {
                businessId = blankToNull(parts[2]);
            }
        }
        return new AgentContext(normalizedScene, businessScene, userId, businessId);
    }

    /**
     * 执行 owner 首次同步心跳，确认 fencing token 仍然有效。
     */
    private void ensureOwnerHeartbeat(String key, String ownerId, long fencingToken) {
        if (!singleFlightService.heartbeat(key, ownerId, fencingToken)) {
            throw new ServiceException("AI single-flight owner lost before model execution");
        }
    }

    /**
     * 将成功回放写入本地 L1 缓存。
     */
    private void cacheReplay(CareerSingleFlightRecordDO record) {
        if (record == null) {
            return;
        }
        localReplayCache.putSuccess(record.getSingleFlightKey(), record, singleFlightProperties.localReplayTtlMillis());
    }

    /**
     * 在 follower 等待窗口内轮询本地 L1 和 single-flight 回放结果。
     */
    private Optional<CareerSingleFlightRecordDO> waitForReplay(String key) {
        long waitTimeoutMillis = singleFlightProperties.waitTimeoutMillis();
        long pollIntervalMillis = singleFlightProperties.pollIntervalMillis();
        long deadline = System.currentTimeMillis() + waitTimeoutMillis;
        Optional<CareerSingleFlightRecordDO> replay = findReplay(key);
        while (replay.isEmpty() && System.currentTimeMillis() < deadline) {
            sleepQuietly(pollIntervalMillis);
            replay = findReplay(key);
        }
        return replay;
    }

    /**
     * 完成 owner 成功状态，若 fencing token 已失效则阻止旧 owner 返回不可回放结果。
     */
    private void completeOwnerSuccess(String key,
                                      String ownerId,
                                      long fencingToken,
                                      String result) {
        String resultJson = wrapResult(result);
        boolean completed = singleFlightService.completeSuccess(key, ownerId, fencingToken, resultJson);
        if (!completed) {
            throw new ServiceException("AI single-flight owner lost before success completion");
        }
    }

    /**
     * 完成 owner 失败状态，若 owner 已被接管则仅记录日志。
     */
    private void completeOwnerFailure(String key, String ownerId, long fencingToken, RuntimeException ex) {
        String errorType = errorType(ex);
        try {
            boolean completed = singleFlightService.completeFailure(key, ownerId, fencingToken, errorType);
            if (!completed) {
                log.warn("AI single-flight owner lost before failure completion: key={}, errorType={}", key, errorType);
            }
        } catch (RuntimeException completionEx) {
            log.warn("AI single-flight failure completion failed, original exception will be preserved: key={}, errorType={}",
                    key, errorType, completionEx);
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

    /**
     * 将空白字符串转换为空值。
     */
    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 安静地等待 follower 轮询间隔。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Interrupted while waiting for single-flight replay");
        }
    }

    private record AgentContext(String scene, String businessScene, String userId, String businessId) {
    }
}
