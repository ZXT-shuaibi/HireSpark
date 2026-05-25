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

public enum InterviewRecoveryScope {

    FLOW_ONLY(false, false),
    SCORE_ONLY(false, false),
    PLAYBACK_ONLY(false, true),
    HOT_RUNTIME(true, true),
    FULL_RUNTIME(true, true);

    private final boolean hotSnapshotReadable;
    private final boolean playbackReadable;

    InterviewRecoveryScope(boolean hotSnapshotReadable, boolean playbackReadable) {
        this.hotSnapshotReadable = hotSnapshotReadable;
        this.playbackReadable = playbackReadable;
    }

    /**
     * 判断当前恢复范围是否允许读取 Redis 热态。
     */
    public boolean canReadHotSnapshot() {
        return hotSnapshotReadable;
    }

    /**
     * 判断当前恢复范围是否需要读取逐轮回放数据。
     */
    public boolean canReadPlayback() {
        return playbackReadable;
    }

    /**
     * 解析空恢复范围，默认使用完整运行态恢复。
     */
    public static InterviewRecoveryScope resolve(InterviewRecoveryScope scope) {
        return scope == null ? FULL_RUNTIME : scope;
    }
}
