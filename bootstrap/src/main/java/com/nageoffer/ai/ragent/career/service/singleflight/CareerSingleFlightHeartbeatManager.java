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

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class CareerSingleFlightHeartbeatManager {

    private static final long MIN_INTERVAL_MILLIS = 10L;
    private static final int HEARTBEAT_THREAD_COUNT = 2;

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(HEARTBEAT_THREAD_COUNT,
            ThreadFactoryBuilder.create()
                    .setNamePrefix("career_single_flight_heartbeat_")
                    .setDaemon(true)
                    .build());

    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * 启动 owner 心跳续租任务，后续会按固定间隔持续刷新 fencing token。
     */
    public String start(String singleFlightKey,
                        String ownerId,
                        long fencingToken,
                        long heartbeatIntervalMillis,
                        BooleanSupplier heartbeatAction) {
        if (StrUtil.isBlank(singleFlightKey) || StrUtil.isBlank(ownerId) || heartbeatAction == null) {
            return null;
        }
        String taskKey = taskKey(singleFlightKey, ownerId, fencingToken);
        stop(taskKey);
        long intervalMillis = Math.max(MIN_INTERVAL_MILLIS, heartbeatIntervalMillis);
        ScheduledFuture<?> future = heartbeatExecutor.scheduleWithFixedDelay(
                () -> beat(taskKey, singleFlightKey, heartbeatAction),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
        futures.put(taskKey, future);
        return taskKey;
    }

    /**
     * 停止指定 owner 的心跳任务。
     */
    public void stop(String taskKey) {
        if (StrUtil.isBlank(taskKey)) {
            return;
        }
        ScheduledFuture<?> future = futures.remove(taskKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭心跳线程池，应用停止时释放后台线程。
     */
    @PreDestroy
    public void shutdown() {
        futures.keySet().forEach(this::stop);
        heartbeatExecutor.shutdownNow();
    }

    /**
     * 执行一次心跳续租，owner 丢失时自动停止后续任务。
     */
    private void beat(String taskKey, String singleFlightKey, BooleanSupplier heartbeatAction) {
        try {
            if (!heartbeatAction.getAsBoolean()) {
                log.warn("Career single-flight 心跳续租失败，停止续租任务：key={}", singleFlightKey);
                stop(taskKey);
            }
        } catch (RuntimeException ex) {
            log.warn("Career single-flight 心跳续租异常，将继续重试：key={}", singleFlightKey, ex);
        }
    }

    /**
     * 构造心跳任务 key，避免同一 owner 被重复调度。
     */
    private String taskKey(String singleFlightKey, String ownerId, long fencingToken) {
        return singleFlightKey + "|" + ownerId + "|" + fencingToken;
    }
}
