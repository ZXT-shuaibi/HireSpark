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
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CareerSingleFlightLocalReplayCache {

    private static final String STATUS_SUCCESS = "SUCCESS";

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 读取本地 L1 成功回放，过期或非成功结果会被忽略。
     */
    public Optional<CareerSingleFlightRecordDO> get(String singleFlightKey) {
        if (StrUtil.isBlank(singleFlightKey)) {
            return Optional.empty();
        }
        CacheEntry entry = cache.get(singleFlightKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expireAtMillis() <= System.currentTimeMillis()) {
            cache.remove(singleFlightKey, entry);
            return Optional.empty();
        }
        return Optional.of(copy(entry.record()));
    }

    /**
     * 写入成功回放快照，失败结果和空结果不会进入本地 L1 缓存。
     */
    public void putSuccess(String singleFlightKey, CareerSingleFlightRecordDO record, long ttlMillis) {
        if (StrUtil.isBlank(singleFlightKey)
                || record == null
                || !STATUS_SUCCESS.equals(record.getStatus())
                || StrUtil.isBlank(record.getResultJson())) {
            return;
        }
        long safeTtlMillis = Math.max(1L, ttlMillis);
        cache.put(singleFlightKey, new CacheEntry(copy(record), System.currentTimeMillis() + safeTtlMillis));
    }

    /**
     * 清理指定 key 的本地回放，主要用于测试或人工治理。
     */
    public void evict(String singleFlightKey) {
        if (StrUtil.isNotBlank(singleFlightKey)) {
            cache.remove(singleFlightKey);
        }
    }

    /**
     * 返回当前缓存条目数量，便于单元测试验证 TTL 行为。
     */
    int size() {
        return cache.size();
    }

    /**
     * 复制审计记录，避免调用方修改缓存中的成功快照。
     */
    private CareerSingleFlightRecordDO copy(CareerSingleFlightRecordDO source) {
        return CareerSingleFlightRecordDO.builder()
                .id(source.getId())
                .singleFlightKey(source.getSingleFlightKey())
                .scene(source.getScene())
                .ownerId(source.getOwnerId())
                .fencingToken(source.getFencingToken())
                .status(source.getStatus())
                .heartbeatTime(copyDate(source.getHeartbeatTime()))
                .requestCount(source.getRequestCount())
                .resultJson(source.getResultJson())
                .errorType(source.getErrorType())
                .traceId(source.getTraceId())
                .createTime(copyDate(source.getCreateTime()))
                .updateTime(copyDate(source.getUpdateTime()))
                .deleted(source.getDeleted())
                .build();
    }

    /**
     * 复制日期对象，避免缓存和调用方共享可变引用。
     */
    private Date copyDate(Date value) {
        return value == null ? null : new Date(value.getTime());
    }

    private record CacheEntry(CareerSingleFlightRecordDO record, long expireAtMillis) {
    }
}
