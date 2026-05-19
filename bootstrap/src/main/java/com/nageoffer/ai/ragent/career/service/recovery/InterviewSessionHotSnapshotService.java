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

package com.nageoffer.ai.ragent.career.service.recovery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

public interface InterviewSessionHotSnapshotService {

    /**
     * 保存面试会话热快照，供恢复链路优先读取。
     */
    void save(HotSnapshot snapshot);

    /**
     * 读取指定用户的面试会话热快照。
     */
    Optional<HotSnapshot> load(String sessionId, String userId);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class HotSnapshot {

        private String sessionId;

        private String userId;

        private Integer version;

        private String status;

        private Integer currentTurnNo;

        private String lastAppliedStepKey;

        private Integer lastTurnSeq;

        private Integer archiveWatermark;

        private Integer scoreCount;

        private String snapshotJson;
    }
}
