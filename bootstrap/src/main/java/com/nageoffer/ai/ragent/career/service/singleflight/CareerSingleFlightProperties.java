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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.ai.single-flight")
public class CareerSingleFlightProperties {

    /**
     * 是否启用 Redis Lua 作为跨节点协调源。
     */
    private boolean enabled = true;

    /**
     * single-flight 运行模式：本地、分布式或混合降级。
     */
    private CareerSingleFlightMode mode = CareerSingleFlightMode.HYBRID;

    /**
     * owner 心跳超过该时间后允许新请求接管。
     */
    private Duration ownerTimeout = Duration.ofMinutes(2);

    /**
     * owner 执行 AI 调用期间的心跳刷新间隔。
     */
    private Duration heartbeatInterval = Duration.ofSeconds(3);

    /**
     * follower 等待 owner 结果回放的最长时间。
     */
    private Duration waitTimeout = Duration.ofSeconds(3);

    /**
     * follower 轮询回放结果的间隔。
     */
    private Duration pollInterval = Duration.ofMillis(100);

    /**
     * Redis 状态保留时间，成功结果也依赖该 TTL 提供短期回放。
     */
    private Duration redisTtl = Duration.ofMinutes(10);

    /**
     * 本地 L1 成功回放缓存的保留时间。
     */
    private Duration localReplayTtl = Duration.ofSeconds(30);

    /**
     * 返回当前生效的 single-flight 运行模式。
     */
    public CareerSingleFlightMode effectiveMode() {
        return mode == null ? CareerSingleFlightMode.HYBRID : mode;
    }

    /**
     * 返回 owner 超时毫秒数，保证传给 Lua 的值为正数。
     */
    public long ownerTimeoutMillis() {
        return positiveMillis(ownerTimeout, Duration.ofMinutes(2));
    }

    /**
     * 返回 owner 心跳刷新间隔毫秒数。
     */
    public long heartbeatIntervalMillis() {
        return positiveMillis(heartbeatInterval, Duration.ofSeconds(3));
    }

    /**
     * 返回 follower 等待毫秒数，保证等待窗口不会为负。
     */
    public long waitTimeoutMillis() {
        return Math.max(0L, safeMillis(waitTimeout, Duration.ofSeconds(3)));
    }

    /**
     * 返回 follower 轮询毫秒数，保证轮询间隔至少 1 毫秒。
     */
    public long pollIntervalMillis() {
        return positiveMillis(pollInterval, Duration.ofMillis(100));
    }

    /**
     * 返回 Redis TTL 毫秒数，保证状态不会无 TTL 写入。
     */
    public long redisTtlMillis() {
        return positiveMillis(redisTtl, Duration.ofMinutes(10));
    }

    /**
     * 返回本地 L1 成功回放 TTL 毫秒数。
     */
    public long localReplayTtlMillis() {
        return positiveMillis(localReplayTtl, Duration.ofSeconds(30));
    }

    private long positiveMillis(Duration duration, Duration fallback) {
        return Math.max(1L, safeMillis(duration, fallback));
    }

    private long safeMillis(Duration duration, Duration fallback) {
        Duration resolved = duration == null ? fallback : duration;
        return resolved.toMillis();
    }
}
