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

import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionSnapshotDO;
import org.springframework.stereotype.Component;

@Component
public class InterviewSnapshotMonotonicGuard {

    /**
     * 合并新旧快照水位，保证轮次、归档水位和评分数量只前进不回退。
     */
    public SnapshotMetrics guard(InterviewSessionSnapshotDO latest, SnapshotMetrics candidate) {
        if (candidate == null) {
            return new SnapshotMetrics(0, 0, 0);
        }
        if (latest == null) {
            return candidate;
        }
        return new SnapshotMetrics(
                max(latest.getLastTurnSeq(), candidate.lastTurnSeq()),
                max(latest.getArchiveWatermark(), candidate.archiveWatermark()),
                max(latest.getScoreCount(), candidate.scoreCount()));
    }

    /**
     * 判断指定 mutation 是否已经写入过冷快照，用于重复补偿时幂等返回。
     */
    public boolean alreadyApplied(InterviewSessionSnapshotDO latest, String mutationId) {
        if (latest == null || mutationId == null || mutationId.isBlank()) {
            return false;
        }
        return mutationId.equals(latest.getLastMutationId())
                || mutationId.equals(latest.getLastAppliedStepKey());
    }

    private int max(Integer left, int right) {
        return Math.max(left == null ? 0 : left, right);
    }

    public record SnapshotMetrics(int lastTurnSeq, int archiveWatermark, int scoreCount) {
    }
}
