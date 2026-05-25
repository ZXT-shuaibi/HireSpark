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

package com.nageoffer.ai.ragent.career.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionSnapshotDO;
import org.apache.ibatis.annotations.Insert;

public interface InterviewSessionSnapshotMapper extends BaseMapper<InterviewSessionSnapshotDO> {

    /**
     * 按快照版本追加写入，版本冲突时直接返回 0，避免 PostgreSQL 事务进入 aborted 状态。
     */
    @Insert("""
            INSERT INTO t_career_interview_session_snapshot (
                id, session_id, user_id, version, material_version, snapshot_json,
                last_applied_step_key, last_mutation_id, last_turn_seq, archive_watermark,
                score_count, last_committed_turn_digest, status, created_by, updated_by
            ) VALUES (
                #{id}, #{sessionId}, #{userId}, #{version}, #{materialVersion},
                #{snapshotJson,typeHandler=com.nageoffer.ai.ragent.knowledge.dao.handler.JsonbTypeHandler},
                #{lastAppliedStepKey}, #{lastMutationId}, #{lastTurnSeq}, #{archiveWatermark},
                #{scoreCount}, #{lastCommittedTurnDigest}, #{status}, #{createdBy}, #{updatedBy}
            )
            ON CONFLICT (session_id, user_id, version) WHERE deleted = 0 DO NOTHING
            """)
    int insertIfVersionAbsent(InterviewSessionSnapshotDO snapshot);
}
