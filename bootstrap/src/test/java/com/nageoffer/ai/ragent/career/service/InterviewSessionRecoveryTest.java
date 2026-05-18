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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionSnapshotDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionSnapshotMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionRecoveryServiceImpl;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InterviewSessionRecoveryTest {

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private InterviewSessionSnapshotMapper snapshotMapper;

    private final List<InterviewSessionDO> sessions = new ArrayList<>();
    private final List<InterviewTurnDO> turns = new ArrayList<>();
    private final List<InterviewSessionSnapshotDO> snapshots = new ArrayList<>();

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewSessionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewTurnDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewSessionSnapshotDO.class);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void recoverRestoresCurrentQuestionFromLatestSnapshotAndPersistedTurns() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();

        CareerInterviewSessionVO result = newService().recover("session-1");

        assertEquals("RUNNING", result.getStatus());
        assertEquals(2, result.getCurrentTurnNo());
        assertEquals("What failed first?", result.getCurrentQuestion().getQuestion());
        assertEquals("FOLLOW_UP", result.getCurrentQuestion().getTurnType());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
        assertEquals("RUNNING", sessions.get(0).getStatus());
    }

    @Test
    void snapshotStableStatePersistsVersionedSnapshotWithLastStepKey() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        InterviewSessionDO session = sessions.get(0);
        session.setCurrentTurnNo(2);

        newService().snapshotStableState(session, "user-1", "session-1:2:rev-1");

        InterviewSessionSnapshotDO latest = snapshots.get(snapshots.size() - 1);
        assertEquals(3, latest.getVersion());
        assertEquals("session-1:2:rev-1", latest.getLastAppliedStepKey());
        assertEquals("PAUSED", latest.getStatus());
    }

    private InterviewSessionRecoveryServiceImpl newService() {
        return new InterviewSessionRecoveryServiceImpl(sessionMapper, turnMapper, snapshotMapper);
    }

    private void login() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
    }

    private void stubPersistence() {
        lenient().when(sessionMapper.selectOne(anySessionWrapper())).thenAnswer(invocation -> sessions.get(0));
        lenient().when(turnMapper.selectList(anyTurnWrapper())).thenAnswer(invocation -> List.copyOf(turns));
        lenient().when(snapshotMapper.selectList(anySnapshotWrapper()))
                .thenAnswer(invocation -> snapshots.isEmpty() ? List.of() : List.of(snapshots.get(snapshots.size() - 1)));
        lenient().doAnswer(invocation -> 1).when(sessionMapper).updateById(any(InterviewSessionDO.class));
        lenient().doAnswer(invocation -> {
            InterviewSessionSnapshotDO snapshot = invocation.getArgument(0);
            if (snapshot.getId() == null) {
                snapshot.setId("snapshot-" + (snapshots.size() + 1));
            }
            snapshots.add(snapshot);
            return 1;
        }).when(snapshotMapper).insert(any(InterviewSessionSnapshotDO.class));
    }

    private void seedSessionTurnsAndSnapshot() {
        sessions.add(InterviewSessionDO.builder()
                .id("session-1")
                .userId("user-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .status("PAUSED")
                .planJson("{\"questions\":[{\"type\":\"PROJECT_DEEP_DIVE\",\"question\":\"Explain your runtime design.\"}]}")
                .currentTurnNo(0)
                .build());
        turns.add(InterviewTurnDO.builder()
                .id("turn-1")
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(1)
                .turnType("PROJECT_DEEP_DIVE")
                .question("Explain your runtime design.")
                .answer("Answer")
                .status("EVALUATED")
                .score(88)
                .build());
        turns.add(InterviewTurnDO.builder()
                .id("turn-2")
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(2)
                .turnType("FOLLOW_UP")
                .question("What failed first?")
                .status("ASKED")
                .build());
        snapshots.add(InterviewSessionSnapshotDO.builder()
                .id("snapshot-1")
                .sessionId("session-1")
                .userId("user-1")
                .version(2)
                .snapshotJson("{\"sessionId\":\"session-1\",\"status\":\"PAUSED\",\"currentTurnNo\":2}")
                .lastAppliedStepKey("session-1:1:rev-1")
                .status("PAUSED")
                .build());
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewSessionDO> anySessionWrapper() {
        return (Wrapper<InterviewSessionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewTurnDO> anyTurnWrapper() {
        return (Wrapper<InterviewTurnDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewSessionSnapshotDO> anySnapshotWrapper() {
        return (Wrapper<InterviewSessionSnapshotDO>) any(Wrapper.class);
    }
}
