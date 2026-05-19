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

package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.recovery.InterviewHotSnapshotRefreshCoordinator;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionHotSnapshotService;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionSnapshotProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InterviewHotSnapshotRefreshCoordinatorTest {

    @Test
    void forceFlushCancelsPendingDebouncedSnapshot() throws Exception {
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);
        InterviewSessionSnapshotProperties properties = new InterviewSessionSnapshotProperties();
        properties.setHotDebounceWindow(Duration.ofMillis(200));
        InterviewHotSnapshotRefreshCoordinator coordinator =
                new InterviewHotSnapshotRefreshCoordinator(hotSnapshotService, properties);
        InterviewSessionHotSnapshotService.HotSnapshot oldSnapshot = snapshot(1);
        InterviewSessionHotSnapshotService.HotSnapshot newSnapshot = snapshot(2);

        try {
            coordinator.refresh(oldSnapshot, false);
            coordinator.refresh(newSnapshot, true);
            Thread.sleep(260L);

            verify(hotSnapshotService, times(1)).save(newSnapshot);
        } finally {
            coordinator.shutdown();
        }
    }

    private InterviewSessionHotSnapshotService.HotSnapshot snapshot(int version) {
        return InterviewSessionHotSnapshotService.HotSnapshot.builder()
                .sessionId("session-1")
                .userId("user-1")
                .version(version)
                .status("RUNNING")
                .currentTurnNo(version)
                .snapshotJson("{\"currentTurnNo\":" + version + "}")
                .build();
    }
}
