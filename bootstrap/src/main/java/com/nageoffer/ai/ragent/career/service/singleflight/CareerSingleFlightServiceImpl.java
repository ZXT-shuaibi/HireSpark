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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerSingleFlightRecordMapper;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CareerSingleFlightServiceImpl implements CareerSingleFlightService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final CareerSingleFlightRecordMapper recordMapper;
    private final CareerSingleFlightRedisCoordinator redisCoordinator;
    private final CareerSingleFlightProperties properties;
    private final CareerSingleFlightLocalReplayCache localReplayCache;

    /**
     * 创建仅依赖数据库的 single-flight 服务，便于测试或 Redis 不可用时兜底。
     */
    public CareerSingleFlightServiceImpl(CareerSingleFlightRecordMapper recordMapper) {
        this(recordMapper, null, new CareerSingleFlightProperties(), new CareerSingleFlightLocalReplayCache());
    }

    /**
     * 创建支持 Redis 协调的 single-flight 服务。
     */
    public CareerSingleFlightServiceImpl(CareerSingleFlightRecordMapper recordMapper,
                                         CareerSingleFlightRedisCoordinator redisCoordinator,
                                         CareerSingleFlightProperties properties) {
        this(recordMapper, redisCoordinator, properties, new CareerSingleFlightLocalReplayCache());
    }

    /**
     * 创建支持 Redis 协调和本地 L1 回放缓存的 single-flight 服务。
     */
    @Autowired
    public CareerSingleFlightServiceImpl(CareerSingleFlightRecordMapper recordMapper,
                                         CareerSingleFlightRedisCoordinator redisCoordinator,
                                         CareerSingleFlightProperties properties,
                                         CareerSingleFlightLocalReplayCache localReplayCache) {
        this.recordMapper = recordMapper;
        this.redisCoordinator = redisCoordinator;
        this.properties = properties == null ? new CareerSingleFlightProperties() : properties;
        this.localReplayCache = localReplayCache == null ? new CareerSingleFlightLocalReplayCache() : localReplayCache;
    }

    /**
     * 尝试抢占 single-flight owner，Redis 可用时优先走 Lua 协调，失败则回落数据库。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public AcquireResult tryAcquire(String scene, String singleFlightKey, String ownerId, String traceId) {
        String key = requireKey(singleFlightKey);
        String owner = StrUtil.blankToDefault(ownerId, UUID.randomUUID().toString());
        Optional<CareerSingleFlightRecordDO> localReplay = readLocalReplay(key);
        if (localReplay.isPresent()) {
            return new AcquireResult(false, true, localReplay.get());
        }
        if (useRedis()) {
            Optional<CareerSingleFlightRedisCoordinator.RedisState> state;
            try {
                state = redisCoordinator.acquire(scene, key, owner, traceId);
            } catch (RuntimeException ex) {
                handleRedisFailure("acquire", key, ex);
                state = Optional.empty();
            }
            if (state.isPresent()) {
                CareerSingleFlightRecordDO record = bestEffortSyncAudit(state.get());
                return toAcquireResult(state.get(), record);
            }
        }
        CareerSingleFlightRecordDO existing = selectByKey(key);
        if (existing == null) {
            CareerSingleFlightRecordDO record = CareerSingleFlightRecordDO.builder()
                    .singleFlightKey(key)
                    .scene(StrUtil.blankToDefault(scene, "CAREER_AI"))
                    .ownerId(owner)
                    .fencingToken(1L)
                    .status(STATUS_RUNNING)
                    .heartbeatTime(new Date())
                    .requestCount(1)
                    .traceId(traceId)
                    .build();
            try {
                recordMapper.insert(record);
                return new AcquireResult(true, false, record);
            } catch (DuplicateKeyException ex) {
                CareerSingleFlightRecordDO duplicate = selectByKey(key);
                if (duplicate != null) {
                    return acquireExisting(scene, owner, traceId, duplicate);
                }
                throw ex;
            }
        }
        return acquireExisting(scene, owner, traceId, existing);
    }

    private AcquireResult acquireExisting(String scene,
                                          String owner,
                                          String traceId,
                                          CareerSingleFlightRecordDO existing) {
        existing.setRequestCount((existing.getRequestCount() == null ? 0 : existing.getRequestCount()) + 1);
        if (STATUS_SUCCESS.equals(existing.getStatus())) {
            recordMapper.updateById(existing);
            cacheSuccessRecord(existing);
            return new AcquireResult(false, true, existing);
        }
        if (STATUS_FAILED.equals(existing.getStatus())) {
            existing.setOwnerId(owner);
            existing.setFencingToken(nextToken(existing));
            existing.setStatus(STATUS_RUNNING);
            existing.setHeartbeatTime(new Date());
            existing.setErrorType(null);
            existing.setResultJson(null);
            existing.setTraceId(traceId);
            existing.setScene(StrUtil.blankToDefault(scene, existing.getScene()));
            recordMapper.updateById(existing);
            return new AcquireResult(true, false, existing);
        }
        if (STATUS_RUNNING.equals(existing.getStatus()) && ownerTimedOut(existing)) {
            existing.setOwnerId(owner);
            existing.setFencingToken(nextToken(existing));
            existing.setHeartbeatTime(new Date());
            existing.setTraceId(traceId);
            existing.setScene(StrUtil.blankToDefault(scene, existing.getScene()));
            recordMapper.updateById(existing);
            return new AcquireResult(true, false, existing);
        }
        recordMapper.updateById(existing);
        return new AcquireResult(false, false, existing);
    }

    /**
     * 刷新 owner 心跳，Redis 可用时优先使用 Lua 原子校验。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public boolean heartbeat(String singleFlightKey, String ownerId, long fencingToken) {
        String key = requireKey(singleFlightKey);
        if (useRedis()) {
            Optional<Boolean> result;
            try {
                result = redisCoordinator.heartbeat(key, ownerId, fencingToken);
            } catch (RuntimeException ex) {
                handleRedisFailure("heartbeat", key, ex);
                result = Optional.empty();
            }
            if (result.isPresent()) {
                if (result.get()) {
                    bestEffortSyncHeartbeat(key);
                }
                return result.get();
            }
        }
        CareerSingleFlightRecordDO record = selectByKey(key);
        if (!owns(record, ownerId, fencingToken)) {
            return false;
        }
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        return true;
    }

    /**
     * 写入 single-flight 成功结果，Redis 成功后同步到数据库审计账本。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public boolean completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson) {
        String key = requireKey(singleFlightKey);
        if (useRedis()) {
            Optional<Boolean> result;
            try {
                result = redisCoordinator.completeSuccess(key, ownerId, fencingToken, resultJson);
            } catch (RuntimeException ex) {
                handleRedisFailure("completeSuccess", key, ex);
                result = Optional.empty();
            }
            if (result.isPresent()) {
                if (result.get()) {
                    bestEffortSyncSuccessAudit(key, ownerId, fencingToken, resultJson);
                }
                return result.get();
            }
        }
        CareerSingleFlightRecordDO record = selectByKey(key);
        if (!owns(record, ownerId, fencingToken)) {
            return false;
        }
        record.setStatus(STATUS_SUCCESS);
        record.setResultJson(resultJson);
        record.setErrorType(null);
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        cacheSuccessRecord(record);
        return true;
    }

    /**
     * 写入 single-flight 失败类型，Redis 成功后同步到数据库审计账本。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public boolean completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType) {
        String key = requireKey(singleFlightKey);
        if (useRedis()) {
            Optional<Boolean> result;
            try {
                result = redisCoordinator.completeFailure(key, ownerId, fencingToken, errorType);
            } catch (RuntimeException ex) {
                handleRedisFailure("completeFailure", key, ex);
                result = Optional.empty();
            }
            if (result.isPresent()) {
                if (result.get()) {
                    bestEffortSyncFailureAudit(key, ownerId, fencingToken, errorType);
                }
                return result.get();
            }
        }
        CareerSingleFlightRecordDO record = selectByKey(key);
        if (!owns(record, ownerId, fencingToken)) {
            return false;
        }
        record.setStatus(STATUS_FAILED);
        record.setErrorType(StrUtil.blankToDefault(errorType, "UNKNOWN"));
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        return true;
    }

    /**
     * 查询可回放的成功结果，Redis 优先，数据库兜底。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public Optional<CareerSingleFlightRecordDO> replayIfAvailable(String singleFlightKey) {
        String key = requireKey(singleFlightKey);
        Optional<CareerSingleFlightRecordDO> localReplay = readLocalReplay(key);
        if (localReplay.isPresent()) {
            return localReplay;
        }
        if (useRedis()) {
            Optional<CareerSingleFlightRedisCoordinator.RedisState> state;
            try {
                state = redisCoordinator.replayIfAvailable(key);
            } catch (RuntimeException ex) {
                handleRedisFailure("replay", key, ex);
                state = null;
            }
            if (state != null) {
                return state.map(this::bestEffortSyncAudit);
            }
        }
        CareerSingleFlightRecordDO record = selectByKey(key);
        if (record == null || !STATUS_SUCCESS.equals(record.getStatus()) || StrUtil.isBlank(record.getResultJson())) {
            return Optional.empty();
        }
        cacheSuccessRecord(record);
        return Optional.of(record);
    }

    private boolean owns(CareerSingleFlightRecordDO record, String ownerId, long fencingToken) {
        return record != null
                && STATUS_RUNNING.equals(record.getStatus())
                && StrUtil.equals(record.getOwnerId(), ownerId)
                && record.getFencingToken() != null
                && record.getFencingToken().longValue() == fencingToken;
    }

    private Long nextToken(CareerSingleFlightRecordDO record) {
        return (record.getFencingToken() == null ? 0L : record.getFencingToken()) + 1;
    }

    private boolean ownerTimedOut(CareerSingleFlightRecordDO record) {
        Date heartbeatTime = record.getHeartbeatTime();
        long ownerTimeoutMillis = properties == null ? 120_000L : properties.ownerTimeoutMillis();
        return heartbeatTime == null || System.currentTimeMillis() - heartbeatTime.getTime() > ownerTimeoutMillis;
    }

    private CareerSingleFlightRecordDO selectByKey(String key) {
        List<CareerSingleFlightRecordDO> records = recordMapper.selectList(
                Wrappers.lambdaQuery(CareerSingleFlightRecordDO.class)
                        .eq(CareerSingleFlightRecordDO::getSingleFlightKey, key)
                        .eq(CareerSingleFlightRecordDO::getDeleted, 0)
                        .orderByDesc(CareerSingleFlightRecordDO::getFencingToken)
                        .last("LIMIT 1"));
        return records == null || records.isEmpty() ? null : records.get(0);
    }

    private String requireKey(String key) {
        if (StrUtil.isBlank(key)) {
            throw new ClientException("Single-flight key is required");
        }
        return key.trim();
    }

    /**
     * 判断当前是否启用 Redis 作为 single-flight 协调源。
     */
    private boolean useRedis() {
        if (properties == null || !properties.isEnabled() || properties.effectiveMode() == CareerSingleFlightMode.LOCAL) {
            return false;
        }
        if (redisCoordinator != null) {
            return true;
        }
        if (properties.effectiveMode() == CareerSingleFlightMode.DISTRIBUTED) {
            throw new ServiceException("Distributed single-flight requires Redis coordinator");
        }
        return false;
    }

    /**
     * 处理 Redis 协调失败，HYBRID 模式回退数据库，DISTRIBUTED 模式直接暴露异常。
     */
    private void handleRedisFailure(String operation, String key, RuntimeException ex) {
        if (properties != null && properties.effectiveMode() == CareerSingleFlightMode.DISTRIBUTED) {
            throw ex;
        }
        log.warn("Redis single-flight {} 失败，HYBRID 回退数据库实现：key={}", operation, key, ex);
    }

    /**
     * 从本地 L1 读取成功回放，并尽力补齐数据库审计账本。
     */
    private Optional<CareerSingleFlightRecordDO> readLocalReplay(String key) {
        Optional<CareerSingleFlightRecordDO> replay = localReplayCache.get(key);
        replay.ifPresent(record -> {
            try {
                syncAuditRecord(record);
            } catch (RuntimeException ex) {
                log.warn("同步本地 single-flight 回放审计失败，继续使用 L1 回放：key={}", key, ex);
            }
        });
        return replay;
    }

    /**
     * 将成功结果写入本地 L1 回放缓存。
     */
    private void cacheSuccessRecord(CareerSingleFlightRecordDO record) {
        if (record == null) {
            return;
        }
        localReplayCache.putSuccess(record.getSingleFlightKey(), record, properties.localReplayTtlMillis());
    }

    /**
     * 尽力同步 Redis 状态到数据库审计账本，同步失败时返回内存快照，不改变 Redis 权威结果。
     */
    private CareerSingleFlightRecordDO bestEffortSyncAudit(CareerSingleFlightRedisCoordinator.RedisState state) {
        CareerSingleFlightRecordDO record = toAuditRecord(state);
        try {
            syncAuditRecord(record);
        } catch (RuntimeException ex) {
            log.warn("同步 single-flight 审计账本失败，保留 Redis 权威状态：key={}", state.singleFlightKey(), ex);
        }
        cacheSuccessRecord(record);
        return record;
    }

    /**
     * 将 Redis 状态快照转换为数据库审计记录。
     */
    private CareerSingleFlightRecordDO toAuditRecord(CareerSingleFlightRedisCoordinator.RedisState state) {
        return CareerSingleFlightRecordDO.builder()
                .singleFlightKey(state.singleFlightKey())
                .scene(state.scene())
                .ownerId(state.ownerId())
                .fencingToken(state.fencingToken())
                .status(state.status())
                .heartbeatTime(state.heartbeatMillis() <= 0 ? null : new Date(state.heartbeatMillis()))
                .requestCount(state.requestCount())
                .resultJson(state.resultJson())
                .errorType(state.errorType())
                .traceId(state.traceId())
                .build();
    }

    /**
     * 同步 single-flight 审计记录到数据库。
     */
    private void syncAuditRecord(CareerSingleFlightRecordDO source) {
        CareerSingleFlightRecordDO record = selectByKey(source.getSingleFlightKey());
        boolean inserting = record == null;
        if (record == null) {
            record = CareerSingleFlightRecordDO.builder()
                    .singleFlightKey(source.getSingleFlightKey())
                    .build();
        }
        record.setSingleFlightKey(source.getSingleFlightKey());
        record.setScene(source.getScene());
        record.setOwnerId(source.getOwnerId());
        record.setFencingToken(source.getFencingToken());
        record.setStatus(source.getStatus());
        record.setHeartbeatTime(source.getHeartbeatTime());
        record.setRequestCount(source.getRequestCount());
        record.setResultJson(source.getResultJson());
        record.setErrorType(source.getErrorType());
        record.setTraceId(source.getTraceId());
        if (inserting) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
        source.setId(record.getId());
        source.setCreateTime(record.getCreateTime());
        source.setUpdateTime(record.getUpdateTime());
        source.setDeleted(record.getDeleted());
    }

    /**
     * 尽力同步 Redis owner 心跳到数据库审计账本。
     */
    private void bestEffortSyncHeartbeat(String key) {
        try {
            CareerSingleFlightRecordDO record = selectByKey(key);
            if (record != null) {
                record.setHeartbeatTime(new Date());
                recordMapper.updateById(record);
            }
        } catch (RuntimeException ex) {
            log.warn("同步 single-flight 心跳审计失败，保留 Redis 权威状态：key={}", key, ex);
        }
    }

    /**
     * 尽力同步 Redis 成功终态到数据库审计账本。
     */
    private void bestEffortSyncSuccessAudit(String key, String ownerId, long fencingToken, String resultJson) {
        try {
            syncSuccessAudit(key, ownerId, fencingToken, resultJson);
        } catch (RuntimeException ex) {
            log.warn("同步 single-flight 成功审计失败，保留 Redis 权威状态：key={}", key, ex);
        }
    }

    /**
     * 尽力同步 Redis 失败终态到数据库审计账本。
     */
    private void bestEffortSyncFailureAudit(String key, String ownerId, long fencingToken, String errorType) {
        try {
            syncFailureAudit(key, ownerId, fencingToken, errorType);
        } catch (RuntimeException ex) {
            log.warn("同步 single-flight 失败审计失败，保留 Redis 权威状态：key={}", key, ex);
        }
    }

    /**
     * 将 Redis 状态快照转换为服务层的抢占结果。
     */
    private AcquireResult toAcquireResult(CareerSingleFlightRedisCoordinator.RedisState state,
                                          CareerSingleFlightRecordDO record) {
        if (state.replayAvailable()) {
            return new AcquireResult(false, true, record);
        }
        if (state.owner()) {
            return new AcquireResult(true, false, record);
        }
        return new AcquireResult(false, false, record);
    }

    /**
     * 同步 single-flight 成功终态到数据库审计账本。
     */
    private void syncSuccessAudit(String key, String ownerId, long fencingToken, String resultJson) {
        CareerSingleFlightRecordDO record = selectByKey(key);
        if (record == null) {
            record = CareerSingleFlightRecordDO.builder()
                    .singleFlightKey(key)
                    .scene(resolveSceneFromKey(key))
                    .ownerId(ownerId)
                    .fencingToken(fencingToken)
                    .status(STATUS_SUCCESS)
                    .resultJson(resultJson)
                    .requestCount(1)
                    .build();
            recordMapper.insert(record);
            cacheSuccessRecord(record);
            return;
        }
        record.setOwnerId(ownerId);
        record.setFencingToken(fencingToken);
        record.setStatus(STATUS_SUCCESS);
        record.setResultJson(resultJson);
        record.setErrorType(null);
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        cacheSuccessRecord(record);
    }

    /**
     * 同步 single-flight 失败终态到数据库审计账本。
     */
    private void syncFailureAudit(String key, String ownerId, long fencingToken, String errorType) {
        CareerSingleFlightRecordDO record = selectByKey(key);
        if (record == null) {
            record = CareerSingleFlightRecordDO.builder()
                    .singleFlightKey(key)
                    .scene(resolveSceneFromKey(key))
                    .ownerId(ownerId)
                    .fencingToken(fencingToken)
                    .status(STATUS_FAILED)
                    .errorType(StrUtil.blankToDefault(errorType, "UNKNOWN"))
                    .requestCount(1)
                    .build();
            recordMapper.insert(record);
            return;
        }
        record.setOwnerId(ownerId);
        record.setFencingToken(fencingToken);
        record.setStatus(STATUS_FAILED);
        record.setErrorType(StrUtil.blankToDefault(errorType, "UNKNOWN"));
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
    }

    /**
     * 从稳定 single-flight key 前缀还原场景名称，无法识别时使用通用场景。
     */
    private String resolveSceneFromKey(String key) {
        if (StrUtil.isBlank(key) || !key.contains(":")) {
            return "CAREER_AI";
        }
        return StrUtil.blankToDefault(StrUtil.subBefore(key, ":", false), "CAREER_AI");
    }
}
