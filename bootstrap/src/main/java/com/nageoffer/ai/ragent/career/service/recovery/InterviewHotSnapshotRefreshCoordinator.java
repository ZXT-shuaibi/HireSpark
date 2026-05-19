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

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewHotSnapshotRefreshCoordinator {

    private final InterviewSessionHotSnapshotService hotSnapshotService;
    private final InterviewSessionSnapshotProperties properties;
    private final Map<String, PendingRefresh> pendingRefreshes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder.create()
                    .setNamePrefix("career_interview_hot_snapshot_")
                    .setDaemon(true)
                    .build());

    /**
     * 提交热快照刷新请求，同一会话在去抖窗口内会合并为最后一次刷新。
     */
    public void refresh(InterviewSessionHotSnapshotService.HotSnapshot snapshot, boolean forceFlush) {
        if (snapshot == null || StrUtil.isBlank(snapshot.getSessionId()) || StrUtil.isBlank(snapshot.getUserId())) {
            return;
        }
        if (forceFlush || properties.resolvedHotDebounceWindow().isZero()) {
            cancelPending(refreshKey(snapshot));
            save(snapshot);
            return;
        }
        String key = refreshKey(snapshot);
        pendingRefreshes.compute(key, (ignored, pending) -> mergePending(key, pending, snapshot));
    }

    /**
     * 关闭去抖刷新线程池，应用停止时释放后台资源。
     */
    @PreDestroy
    public void shutdown() {
        pendingRefreshes.values().forEach(pending -> pending.future().cancel(false));
        pendingRefreshes.clear();
        refreshExecutor.shutdownNow();
    }

    private PendingRefresh mergePending(String key,
                                        PendingRefresh current,
                                        InterviewSessionHotSnapshotService.HotSnapshot snapshot) {
        if (current != null) {
            return current.withSnapshot(snapshot);
        }
        ScheduledFuture<?> future = refreshExecutor.schedule(() -> flush(key),
                properties.resolvedHotDebounceWindow().toMillis(),
                TimeUnit.MILLISECONDS);
        return new PendingRefresh(snapshot, future);
    }

    private void flush(String key) {
        PendingRefresh pending = pendingRefreshes.remove(key);
        if (pending == null) {
            return;
        }
        save(pending.snapshot());
    }

    /**
     * 取消同一会话的待刷新任务，避免旧热快照覆盖强制刷新的新版本。
     */
    private void cancelPending(String key) {
        PendingRefresh pending = pendingRefreshes.remove(key);
        if (pending != null) {
            pending.future().cancel(false);
        }
    }

    private void save(InterviewSessionHotSnapshotService.HotSnapshot snapshot) {
        try {
            hotSnapshotService.save(snapshot);
        } catch (RuntimeException ex) {
            log.warn("面试热快照去抖刷新失败，sessionId={}，原因={}", snapshot.getSessionId(), ex.getMessage());
        }
    }

    private String refreshKey(InterviewSessionHotSnapshotService.HotSnapshot snapshot) {
        return snapshot.getUserId() + ":" + snapshot.getSessionId();
    }

    private record PendingRefresh(InterviewSessionHotSnapshotService.HotSnapshot snapshot,
                                  ScheduledFuture<?> future) {

        private PendingRefresh withSnapshot(InterviewSessionHotSnapshotService.HotSnapshot newSnapshot) {
            return new PendingRefresh(newSnapshot, future);
        }
    }
}
