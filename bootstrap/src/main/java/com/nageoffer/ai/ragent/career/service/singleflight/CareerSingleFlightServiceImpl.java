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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CareerSingleFlightServiceImpl implements CareerSingleFlightService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final long OWNER_TIMEOUT_MILLIS = 120_000L;

    private final CareerSingleFlightRecordMapper recordMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public AcquireResult tryAcquire(String scene, String singleFlightKey, String ownerId, String traceId) {
        String key = requireKey(singleFlightKey);
        String owner = StrUtil.blankToDefault(ownerId, UUID.randomUUID().toString());
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
                    return acquireExisting(owner, traceId, duplicate);
                }
                throw ex;
            }
        }
        return acquireExisting(owner, traceId, existing);
    }

    private AcquireResult acquireExisting(String owner,
                                          String traceId,
                                          CareerSingleFlightRecordDO existing) {
        existing.setRequestCount((existing.getRequestCount() == null ? 0 : existing.getRequestCount()) + 1);
        if (STATUS_SUCCESS.equals(existing.getStatus())) {
            recordMapper.updateById(existing);
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
            recordMapper.updateById(existing);
            return new AcquireResult(true, false, existing);
        }
        if (STATUS_RUNNING.equals(existing.getStatus()) && ownerTimedOut(existing)) {
            existing.setOwnerId(owner);
            existing.setFencingToken(nextToken(existing));
            existing.setHeartbeatTime(new Date());
            existing.setTraceId(traceId);
            recordMapper.updateById(existing);
            return new AcquireResult(true, false, existing);
        }
        recordMapper.updateById(existing);
        return new AcquireResult(false, false, existing);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public boolean heartbeat(String singleFlightKey, String ownerId, long fencingToken) {
        CareerSingleFlightRecordDO record = selectByKey(requireKey(singleFlightKey));
        if (!owns(record, ownerId, fencingToken)) {
            return false;
        }
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        return true;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public boolean completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson) {
        CareerSingleFlightRecordDO record = selectByKey(requireKey(singleFlightKey));
        if (!owns(record, ownerId, fencingToken)) {
            return false;
        }
        record.setStatus(STATUS_SUCCESS);
        record.setResultJson(resultJson);
        record.setErrorType(null);
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        return true;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public boolean completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType) {
        CareerSingleFlightRecordDO record = selectByKey(requireKey(singleFlightKey));
        if (!owns(record, ownerId, fencingToken)) {
            return false;
        }
        record.setStatus(STATUS_FAILED);
        record.setErrorType(StrUtil.blankToDefault(errorType, "UNKNOWN"));
        record.setHeartbeatTime(new Date());
        recordMapper.updateById(record);
        return true;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public Optional<CareerSingleFlightRecordDO> replayIfAvailable(String singleFlightKey) {
        CareerSingleFlightRecordDO record = selectByKey(requireKey(singleFlightKey));
        if (record == null || !STATUS_SUCCESS.equals(record.getStatus()) || StrUtil.isBlank(record.getResultJson())) {
            return Optional.empty();
        }
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
        return heartbeatTime == null || System.currentTimeMillis() - heartbeatTime.getTime() > OWNER_TIMEOUT_MILLIS;
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
}
