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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CareerSingleFlightRedisCoordinator {

    private static final String KEY_PREFIX = "career:single-flight:";
    private static final String ACQUIRE_LUA = "lua/career_single_flight_acquire.lua";
    private static final String HEARTBEAT_LUA = "lua/career_single_flight_heartbeat.lua";
    private static final String COMPLETE_LUA = "lua/career_single_flight_complete.lua";
    private static final String REPLAY_LUA = "lua/career_single_flight_replay.lua";

    private final StringRedisTemplate stringRedisTemplate;
    private final CareerSingleFlightProperties properties;

    private final DefaultRedisScript<List> acquireScript = listScript(ACQUIRE_LUA);
    private final DefaultRedisScript<List> heartbeatScript = listScript(HEARTBEAT_LUA);
    private final DefaultRedisScript<List> completeScript = listScript(COMPLETE_LUA);
    private final DefaultRedisScript<List> replayScript = listScript(REPLAY_LUA);

    /**
     * 原子抢占 owner 权限，返回 Redis 中的最新状态快照。
     */
    public Optional<RedisState> acquire(String scene, String singleFlightKey, String ownerId, String traceId) {
        List<Object> result = execute(acquireScript,
                redisKey(singleFlightKey),
                StrUtil.blankToDefault(scene, "CAREER_AI"),
                singleFlightKey,
                ownerId,
                StrUtil.blankToDefault(traceId, ""),
                String.valueOf(properties.ownerTimeoutMillis()),
                String.valueOf(properties.redisTtlMillis()),
                String.valueOf(System.currentTimeMillis()));
        return toState(result, scene, singleFlightKey);
    }

    /**
     * 原子刷新 owner 心跳，仅当前 owner 与 fencing token 匹配时成功。
     */
    public Optional<Boolean> heartbeat(String singleFlightKey, String ownerId, long fencingToken) {
        List<Object> result = execute(heartbeatScript,
                redisKey(singleFlightKey),
                ownerId,
                String.valueOf(fencingToken),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(properties.redisTtlMillis()));
        return toBoolean(result);
    }

    /**
     * 原子写入成功结果，仅当前 owner 与 fencing token 匹配时成功。
     */
    public Optional<Boolean> completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson) {
        return complete(singleFlightKey, ownerId, fencingToken, "SUCCESS", resultJson, "");
    }

    /**
     * 原子写入失败类型，仅当前 owner 与 fencing token 匹配时成功。
     */
    public Optional<Boolean> completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType) {
        return complete(singleFlightKey, ownerId, fencingToken, "FAILED", "", StrUtil.blankToDefault(errorType, "UNKNOWN"));
    }

    /**
     * 查询 Redis 中是否已有可回放成功结果。
     */
    public Optional<RedisState> replayIfAvailable(String singleFlightKey) {
        List<Object> result = execute(replayScript, redisKey(singleFlightKey));
        return toState(result, null, singleFlightKey);
    }

    private Optional<Boolean> complete(String singleFlightKey,
                                       String ownerId,
                                       long fencingToken,
                                       String status,
                                       String resultJson,
                                       String errorType) {
        List<Object> result = execute(completeScript,
                redisKey(singleFlightKey),
                ownerId,
                String.valueOf(fencingToken),
                status,
                StrUtil.blankToDefault(resultJson, ""),
                StrUtil.blankToDefault(errorType, ""),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(properties.redisTtlMillis()));
        return toBoolean(result);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> execute(DefaultRedisScript<List> script, String key, String... args) {
        return (List<Object>) (List) stringRedisTemplate.execute(script, List.of(key), (Object[]) args);
    }

    private Optional<Boolean> toBoolean(List<Object> result) {
        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseLong(result.get(0)) == 1L);
    }

    private Optional<RedisState> toState(List<Object> result, String fallbackScene, String fallbackKey) {
        if (result == null || result.isEmpty() || "NONE".equals(asString(result.get(0)))) {
            return Optional.empty();
        }
        String action = asString(result.get(0));
        String status = asString(result, 1);
        String ownerId = asString(result, 2);
        long fencingToken = parseLong(valueAt(result, 3));
        int requestCount = (int) parseLong(valueAt(result, 4));
        String resultJson = blankToNull(asString(result, 5));
        String errorType = blankToNull(asString(result, 6));
        String traceId = blankToNull(asString(result, 7));
        long heartbeatMillis = parseLong(valueAt(result, 8));
        String scene = blankToNull(asString(result, 9));
        String singleFlightKey = blankToNull(asString(result, 10));
        return Optional.of(new RedisState("OWNER".equals(action),
                "REPLAY".equals(action),
                StrUtil.blankToDefault(scene, fallbackScene),
                StrUtil.blankToDefault(singleFlightKey, fallbackKey),
                ownerId,
                fencingToken,
                status,
                heartbeatMillis,
                requestCount,
                resultJson,
                errorType,
                traceId));
    }

    private String redisKey(String singleFlightKey) {
        return KEY_PREFIX + singleFlightKey;
    }

    private static DefaultRedisScript<List> listScript(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(List.class);
        return script;
    }

    private Object valueAt(List<Object> result, int index) {
        return result.size() > index ? result.get(index) : null;
    }

    private String asString(List<Object> result, int index) {
        return asString(valueAt(result, index));
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            log.debug("Redis single-flight 数字解析失败：{}", value);
            return 0L;
        }
    }

    /**
     * Redis single-flight 状态快照。
     */
    public record RedisState(boolean owner,
                             boolean replayAvailable,
                             String scene,
                             String singleFlightKey,
                             String ownerId,
                             long fencingToken,
                             String status,
                             long heartbeatMillis,
                             int requestCount,
                             String resultJson,
                             String errorType,
                             String traceId) {
    }
}
