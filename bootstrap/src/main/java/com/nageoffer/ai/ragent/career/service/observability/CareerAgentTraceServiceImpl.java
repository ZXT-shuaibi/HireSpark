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

package com.nageoffer.ai.ragent.career.service.observability;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentExecutionTraceDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentSessionStatsDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentToolInvocationDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerAgentExecutionTraceMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerAgentSessionStatsMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerAgentToolInvocationMapper;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CareerAgentTraceServiceImpl implements CareerAgentTraceService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int SUMMARY_MAX_LENGTH = 1024;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final CareerAgentExecutionTraceMapper executionTraceMapper;

    private final CareerAgentToolInvocationMapper toolInvocationMapper;

    private final CareerAgentSessionStatsMapper sessionStatsMapper;

    /**
     * 开始记录一次 Career Agent 调用，只保存摘要和哈希，不保存完整输入正文。
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CareerAgentExecutionTraceDO startExecution(CareerAgentTraceCommand command) {
        CareerAgentTraceCommand safeCommand = command == null ? new CareerAgentTraceCommand() : command;
        CareerAgentExecutionTraceDO trace = CareerAgentExecutionTraceDO.builder()
                .agentType(normalize(safeCommand.getAgentType(), "CAREER_AGENT"))
                .scene(normalize(safeCommand.getScene(), "CAREER"))
                .sessionId(blankToNull(safeCommand.getSessionId()))
                .userId(blankToNull(safeCommand.getUserId()))
                .traceId(blankToNull(safeCommand.getTraceId()))
                .modelName(blankToNull(safeCommand.getModelName()))
                .status(STATUS_RUNNING)
                .inputSummary(summarize(safeCommand.getInput()))
                .latencyMs(0L)
                .build();
        executionTraceMapper.insert(trace);
        return trace;
    }

    /**
     * 将 Agent 调用标记为成功，并累加同会话的成功统计。
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void finishSuccess(CareerAgentExecutionTraceDO trace, Object output, long latencyMs) {
        if (trace == null) {
            return;
        }
        trace.setStatus(STATUS_SUCCESS);
        trace.setOutputSummary(summarize(output));
        trace.setLatencyMs(Math.max(0L, latencyMs));
        trace.setErrorType(null);
        trace.setErrorMessage(null);
        executionTraceMapper.updateById(trace);
        updateSessionStats(trace, true, Math.max(0L, latencyMs));
    }

    /**
     * 将 Agent 调用标记为失败，并累加同会话的失败统计。
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void finishFailed(CareerAgentExecutionTraceDO trace, RuntimeException ex, long latencyMs) {
        if (trace == null) {
            return;
        }
        trace.setStatus(STATUS_FAILED);
        trace.setLatencyMs(Math.max(0L, latencyMs));
        trace.setErrorType(errorType(ex));
        trace.setErrorMessage(limit(errorMessage(ex), ERROR_MESSAGE_MAX_LENGTH));
        executionTraceMapper.updateById(trace);
        updateSessionStats(trace, false, Math.max(0L, latencyMs));
    }

    /**
     * 记录工具或检索调用，并通过摘要避免敏感正文完整入库。
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recordToolInvocation(CareerAgentToolInvocationCommand command) {
        if (command == null) {
            return;
        }
        CareerAgentToolInvocationDO invocation = CareerAgentToolInvocationDO.builder()
                .executionTraceId(blankToNull(command.getExecutionTraceId()))
                .traceId(blankToNull(command.getTraceId()))
                .toolType(normalize(command.getToolType(), "TOOL"))
                .toolName(normalize(command.getToolName(), "UNKNOWN_TOOL"))
                .inputSummary(summarize(command.getInput()))
                .outputSummary(summarize(command.getOutput()))
                .status(normalize(command.getStatus(), STATUS_SUCCESS))
                .latencyMs(Math.max(0L, Objects.requireNonNullElse(command.getLatencyMs(), 0L)))
                .errorMessage(limit(command.getErrorMessage(), ERROR_MESSAGE_MAX_LENGTH))
                .build();
        toolInvocationMapper.insert(invocation);
    }

    /**
     * 查询最近 Agent 调用记录，支持按 Agent 类型和状态过滤。
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<CareerAgentExecutionTraceDO> listRecentExecutions(Integer limit, String agentType, String status) {
        int resolvedLimit = normalizeLimit(limit);
        LambdaQueryWrapper<CareerAgentExecutionTraceDO> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(agentType)) {
            wrapper.eq(CareerAgentExecutionTraceDO::getAgentType, agentType.trim());
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(CareerAgentExecutionTraceDO::getStatus, status.trim());
        }
        wrapper.orderByDesc(CareerAgentExecutionTraceDO::getUpdateTime)
                .last("limit " + resolvedLimit);
        return executionTraceMapper.selectList(wrapper);
    }

    /**
     * 更新同一会话和场景下的聚合统计。
     */
    private void updateSessionStats(CareerAgentExecutionTraceDO trace, boolean success, long latencyMs) {
        if (StrUtil.isBlank(trace.getSessionId())) {
            return;
        }
        LambdaQueryWrapper<CareerAgentSessionStatsDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CareerAgentSessionStatsDO::getSessionId, trace.getSessionId())
                .eq(CareerAgentSessionStatsDO::getScene, trace.getScene())
                .last("limit 1");
        CareerAgentSessionStatsDO stats = sessionStatsMapper.selectOne(wrapper);
        if (stats == null) {
            stats = CareerAgentSessionStatsDO.builder()
                    .sessionId(trace.getSessionId())
                    .userId(trace.getUserId())
                    .scene(trace.getScene())
                    .totalCalls(1L)
                    .successCalls(success ? 1L : 0L)
                    .failedCalls(success ? 0L : 1L)
                    .totalLatencyMs(latencyMs)
                    .lastAgentType(trace.getAgentType())
                    .lastStatus(trace.getStatus())
                    .lastTraceId(trace.getTraceId())
                    .build();
            sessionStatsMapper.insert(stats);
            return;
        }
        stats.setTotalCalls(plus(stats.getTotalCalls(), 1L));
        stats.setSuccessCalls(plus(stats.getSuccessCalls(), success ? 1L : 0L));
        stats.setFailedCalls(plus(stats.getFailedCalls(), success ? 0L : 1L));
        stats.setTotalLatencyMs(plus(stats.getTotalLatencyMs(), latencyMs));
        stats.setLastAgentType(trace.getAgentType());
        stats.setLastStatus(trace.getStatus());
        stats.setLastTraceId(trace.getTraceId());
        sessionStatsMapper.updateById(stats);
    }

    /**
     * 对输入输出生成脱敏摘要，避免完整 Prompt、简历或 JD 入库。
     */
    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ChatRequest request) {
            return summarizeChatRequest(request);
        }
        String text = String.valueOf(value);
        return limit("chars=" + text.length() + ", sha256=" + sha256(text), SUMMARY_MAX_LENGTH);
    }

    /**
     * 对大模型请求生成结构化摘要。
     */
    private String summarizeChatRequest(ChatRequest request) {
        List<String> contents = request.getMessages() == null
                ? List.of()
                : request.getMessages().stream()
                        .map(ChatMessage::getContent)
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .toList();
        String joined = String.join("\n", contents);
        return limit("messages=" + contents.size()
                + ", chars=" + joined.length()
                + ", sha256=" + sha256(joined).substring(0, 16)
                + ", temperature=" + (request.getTemperature() == null ? "default" : request.getTemperature())
                + ", enableTools=" + (request.getEnableTools() == null ? "default" : request.getEnableTools()),
                SUMMARY_MAX_LENGTH);
    }

    /**
     * 计算文本 SHA-256 摘要。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(StrUtil.blankToDefault(value, "")
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return "hash-unavailable";
        }
    }

    /**
     * 提取异常类型。
     */
    private String errorType(RuntimeException ex) {
        return ex == null ? "UNKNOWN" : normalize(ex.getClass().getSimpleName(), "UNKNOWN");
    }

    /**
     * 提取异常消息。
     */
    private String errorMessage(RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        return StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName());
    }

    /**
     * 标准化短文本字段。
     */
    private String normalize(String value, String defaultValue) {
        return StrUtil.blankToDefault(value, defaultValue).trim();
    }

    /**
     * 将空白字符串转换为空值。
     */
    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 限制字符串长度，避免观测字段过长。
     */
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 处理空值加法。
     */
    private long plus(Long left, long right) {
        return Objects.requireNonNullElse(left, 0L) + right;
    }

    /**
     * 标准化管理端查询条数。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
