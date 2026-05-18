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

import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;

import java.util.Optional;

public interface CareerSingleFlightService {

    /**
     * 尝试获取 single-flight owner 权限，或返回可回放结果状态。
     */
    AcquireResult tryAcquire(String scene, String singleFlightKey, String ownerId, String traceId);

    /**
     * 刷新 owner 心跳，证明当前 fencing token 仍然有效。
     */
    boolean heartbeat(String singleFlightKey, String ownerId, long fencingToken);

    /**
     * 以 owner 身份写入成功结果。
     */
    boolean completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson);

    /**
     * 以 owner 身份写入失败类型。
     */
    boolean completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType);

    /**
     * 查询是否存在可直接回放的成功结果。
     */
    Optional<CareerSingleFlightRecordDO> replayIfAvailable(String singleFlightKey);

    record AcquireResult(boolean owner,
                         boolean replayAvailable,
                         CareerSingleFlightRecordDO record) {
    }
}
