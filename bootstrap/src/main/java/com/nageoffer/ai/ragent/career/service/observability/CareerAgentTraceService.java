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

import com.nageoffer.ai.ragent.career.dao.entity.CareerAgentExecutionTraceDO;

import java.util.List;

public interface CareerAgentTraceService {

    /**
     * 开始记录一次 Career Agent 调用。
     */
    CareerAgentExecutionTraceDO startExecution(CareerAgentTraceCommand command);

    /**
     * 将 Agent 调用标记为成功并更新会话统计。
     */
    void finishSuccess(CareerAgentExecutionTraceDO trace, Object output, long latencyMs);

    /**
     * 将 Agent 调用标记为失败并更新会话统计。
     */
    void finishFailed(CareerAgentExecutionTraceDO trace, RuntimeException ex, long latencyMs);

    /**
     * 记录挂靠在 Agent 调用下的工具或检索调用。
     */
    void recordToolInvocation(CareerAgentToolInvocationCommand command);

    /**
     * 查询最近的 Agent 调用记录，供管理端排查使用。
     */
    List<CareerAgentExecutionTraceDO> listRecentExecutions(Integer limit, String agentType, String status);
}
