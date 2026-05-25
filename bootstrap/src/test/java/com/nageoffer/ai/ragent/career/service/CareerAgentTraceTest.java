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

package com.nageoffer.ai.ragent.career.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentExecutionTraceDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentSessionStatsDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentToolInvocationDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerAgentExecutionTraceMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerAgentSessionStatsMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerAgentToolInvocationMapper;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentTraceCommand;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentTraceService;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentTraceServiceImpl;
import com.nageoffer.ai.ragent.career.service.observability.CareerAgentToolInvocationCommand;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerAgentTraceTest {

    @Mock
    private CareerAgentExecutionTraceMapper executionTraceMapper;

    @Mock
    private CareerAgentToolInvocationMapper toolInvocationMapper;

    @Mock
    private CareerAgentSessionStatsMapper sessionStatsMapper;

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerAgentExecutionTraceDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerAgentToolInvocationDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerAgentSessionStatsDO.class);
    }

    @Test
    void recordsSuccessfulAgentExecutionWithSanitizedSummaryAndStats() {
        when(sessionStatsMapper.selectOne(any())).thenReturn(null);
        CareerAgentTraceService service = newService();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("候选人手机号 13800000000，负责订单系统重构")))
                .temperature(0.2)
                .build();

        CareerAgentExecutionTraceDO trace = service.startExecution(CareerAgentTraceCommand.builder()
                .agentType("OPTIMIZATION_EXECUTOR")
                .scene("OPTIMIZATION")
                .sessionId("task-1")
                .userId("user-1")
                .traceId("trace-1")
                .modelName("RagentModelRouter")
                .input(request)
                .build());

        ArgumentCaptor<CareerAgentExecutionTraceDO> startCaptor =
                ArgumentCaptor.forClass(CareerAgentExecutionTraceDO.class);
        verify(executionTraceMapper).insert(startCaptor.capture());
        CareerAgentExecutionTraceDO started = startCaptor.getValue();
        assertEquals("OPTIMIZATION_EXECUTOR", started.getAgentType());
        assertEquals("OPTIMIZATION", started.getScene());
        assertEquals("RUNNING", started.getStatus());
        assertTrue(started.getInputSummary().contains("messages=1"));
        assertTrue(started.getInputSummary().contains("sha256="));
        assertFalse(started.getInputSummary().contains("13800000000"));

        trace.setId("agent-trace-1");
        service.finishSuccess(trace, "优化后的简历正文不应完整落库", 321L);

        assertEquals("SUCCESS", trace.getStatus());
        assertEquals(321L, trace.getLatencyMs());
        assertTrue(trace.getOutputSummary().contains("sha256="));
        assertFalse(trace.getOutputSummary().contains("优化后的简历正文不应完整落库"));
        verify(executionTraceMapper).updateById(trace);

        ArgumentCaptor<CareerAgentSessionStatsDO> statsCaptor =
                ArgumentCaptor.forClass(CareerAgentSessionStatsDO.class);
        verify(sessionStatsMapper).insert(statsCaptor.capture());
        CareerAgentSessionStatsDO stats = statsCaptor.getValue();
        assertEquals("task-1", stats.getSessionId());
        assertEquals("OPTIMIZATION", stats.getScene());
        assertEquals(1L, stats.getTotalCalls());
        assertEquals(1L, stats.getSuccessCalls());
        assertEquals(0L, stats.getFailedCalls());
        assertEquals(321L, stats.getTotalLatencyMs());
        assertEquals("SUCCESS", stats.getLastStatus());
    }

    @Test
    void recordsToolInvocationUnderExecutionTrace() {
        CareerAgentTraceService service = newService();

        service.recordToolInvocation(CareerAgentToolInvocationCommand.builder()
                .executionTraceId("agent-trace-1")
                .traceId("trace-1")
                .toolType("RETRIEVAL")
                .toolName("CareerRerankRetriever")
                .input("完整 JD 与简历不应直接落库")
                .output("Top3 evidence")
                .status("SUCCESS")
                .latencyMs(45L)
                .build());

        ArgumentCaptor<CareerAgentToolInvocationDO> toolCaptor =
                ArgumentCaptor.forClass(CareerAgentToolInvocationDO.class);
        verify(toolInvocationMapper).insert(toolCaptor.capture());
        CareerAgentToolInvocationDO invocation = toolCaptor.getValue();
        assertEquals("agent-trace-1", invocation.getExecutionTraceId());
        assertEquals("RETRIEVAL", invocation.getToolType());
        assertEquals("CareerRerankRetriever", invocation.getToolName());
        assertEquals("SUCCESS", invocation.getStatus());
        assertTrue(invocation.getInputSummary().contains("sha256="));
        assertFalse(invocation.getInputSummary().contains("完整 JD 与简历不应直接落库"));
    }

    @Test
    void listsRecentAgentExecutionsForAdminQuery() {
        CareerAgentExecutionTraceDO trace = CareerAgentExecutionTraceDO.builder()
                .id("agent-trace-1")
                .agentType("INTERVIEW_EVALUATE")
                .scene("INTERVIEW")
                .status("SUCCESS")
                .build();
        when(executionTraceMapper.selectList(any())).thenReturn(List.of(trace));

        List<CareerAgentExecutionTraceDO> traces =
                newService().listRecentExecutions(200, "INTERVIEW_EVALUATE", "SUCCESS");

        assertEquals(1, traces.size());
        assertEquals("agent-trace-1", traces.get(0).getId());
        verify(executionTraceMapper).selectList(any());
    }

    private CareerAgentTraceService newService() {
        return new CareerAgentTraceServiceImpl(executionTraceMapper, toolInvocationMapper, sessionStatsMapper);
    }
}
