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

package com.nageoffer.ai.ragent.career.service.trace;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CareerTraceRunner {

    private static final String ENTRY_METHOD = "CareerTraceRunner#run";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    public <T> T run(String traceName, String taskId, Supplier<T> supplier) {
        if (!traceProperties.isEnabled()) {
            return supplier.get();
        }

        String traceId = IdUtil.getSnowflakeNextIdStr();
        long startMillis = System.currentTimeMillis();
        traceRecordService.startRun(RagTraceRunDO.builder()
                .traceId(traceId)
                .traceName(traceName)
                .entryMethod(ENTRY_METHOD)
                .taskId(taskId)
                .userId(UserContext.getUserId())
                .status(STATUS_RUNNING)
                .startTime(new Date())
                .build());

        RagTraceContext.setTraceId(traceId);
        RagTraceContext.setTaskId(taskId);
        try {
            T result = supplier.get();
            traceRecordService.finishRun(
                    traceId,
                    STATUS_SUCCESS,
                    null,
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
            return result;
        } catch (Throwable ex) {
            traceRecordService.finishRun(
                    traceId,
                    STATUS_ERROR,
                    truncateError(ex),
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
            throw propagate(ex);
        } finally {
            RagTraceContext.clear();
        }
    }

    private String truncateError(Throwable ex) {
        String message = ex.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(ex.getMessage(), "");
        int max = traceProperties.getMaxErrorLength();
        return message.length() <= max ? message : message.substring(0, max);
    }

    private RuntimeException propagate(Throwable ex) {
        if (ex instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (ex instanceof Error error) {
            throw error;
        }
        return new ServiceException("Career trace execution failed", ex, BaseErrorCode.SERVICE_ERROR);
    }
}
