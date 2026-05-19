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
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionHotSnapshotService;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionRecoveryServiceImpl;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

    @Test
    void snapshotStableStateWritesHotSnapshotAfterColdSnapshot() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        InterviewSessionDO session = sessions.get(0);
        session.setCurrentTurnNo(2);
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);

        newService(hotSnapshotService).snapshotStableState(session, "user-1", "session-1:2:rev-1");

        ArgumentCaptor<InterviewSessionHotSnapshotService.HotSnapshot> captor =
                ArgumentCaptor.forClass(InterviewSessionHotSnapshotService.HotSnapshot.class);
        verify(hotSnapshotService).save(captor.capture());
        InterviewSessionHotSnapshotService.HotSnapshot hotSnapshot = captor.getValue();
        assertEquals("session-1", hotSnapshot.getSessionId());
        assertEquals("user-1", hotSnapshot.getUserId());
        assertEquals(3, hotSnapshot.getVersion());
        assertEquals("PAUSED", hotSnapshot.getStatus());
        assertEquals(2, hotSnapshot.getCurrentTurnNo());
        assertEquals("session-1:2:rev-1", hotSnapshot.getLastAppliedStepKey());
        assertEquals(2, hotSnapshot.getLastTurnSeq());
        assertEquals(2, hotSnapshot.getArchiveWatermark());
        assertEquals(1, hotSnapshot.getScoreCount());
        assertEquals(snapshots.get(snapshots.size() - 1).getSnapshotJson(), hotSnapshot.getSnapshotJson());
    }

    @Test
    void snapshotStableStateWritesHotSnapshotAfterTransactionCommit() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        InterviewSessionDO session = sessions.get(0);
        session.setCurrentTurnNo(2);
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);
        TransactionSynchronizationManager.initSynchronization();

        newService(hotSnapshotService).snapshotStableState(session, "user-1", "session-1:2:rev-1");

        verify(hotSnapshotService, never()).save(any());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.get(0).afterCommit();
        verify(hotSnapshotService).save(any());
    }

    @Test
    void recoverPrefersHigherVersionHotSnapshot() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);
        when(hotSnapshotService.load("session-1", "user-1")).thenReturn(Optional.of(
                InterviewSessionHotSnapshotService.HotSnapshot.builder()
                        .sessionId("session-1")
                        .userId("user-1")
                        .version(3)
                        .status("COMPLETED")
                        .currentTurnNo(2)
                        .lastAppliedStepKey("session-1:2:rev-2")
                        .lastTurnSeq(2)
                        .archiveWatermark(2)
                        .scoreCount(1)
                        .snapshotJson("{\"sessionId\":\"session-1\",\"status\":\"COMPLETED\",\"currentTurnNo\":2}")
                        .build()));

        CareerInterviewSessionVO result = newService(hotSnapshotService).recover("session-1");

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(2, result.getCurrentTurnNo());
        assertEquals("What failed first?", result.getCurrentQuestion().getQuestion());
        assertEquals(2, sessions.get(0).getCurrentTurnNo());
        assertEquals("COMPLETED", sessions.get(0).getStatus());
    }

    @Test
    void recoverDoesNotRegressCurrentTurnNoFromHotOrColdSnapshot() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        sessions.get(0).setCurrentTurnNo(3);
        turns.add(InterviewTurnDO.builder()
                .id("turn-3")
                .sessionId("session-1")
                .userId("user-1")
                .turnNo(3)
                .turnType("FOLLOW_UP")
                .question("What changed next?")
                .status("ASKED")
                .build());
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);
        when(hotSnapshotService.load("session-1", "user-1")).thenReturn(Optional.of(
                InterviewSessionHotSnapshotService.HotSnapshot.builder()
                        .sessionId("session-1")
                        .userId("user-1")
                        .version(3)
                        .status("PAUSED")
                        .currentTurnNo(2)
                        .lastTurnSeq(2)
                        .archiveWatermark(2)
                        .scoreCount(1)
                        .snapshotJson("{\"sessionId\":\"session-1\",\"status\":\"PAUSED\",\"currentTurnNo\":2}")
                        .build()));

        CareerInterviewSessionVO result = newService(hotSnapshotService).recover("session-1");

        assertEquals(3, result.getCurrentTurnNo());
        assertEquals("What changed next?", result.getCurrentQuestion().getQuestion());
        assertEquals(3, sessions.get(0).getCurrentTurnNo());
    }

    @Test
    void snapshotStableStateKeepsColdSnapshotWhenHotSnapshotFails() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        InterviewSessionDO session = sessions.get(0);
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);
        doThrow(new IllegalStateException("redis down")).when(hotSnapshotService).save(any());

        newService(hotSnapshotService).snapshotStableState(session, "user-1", "session-1:2:rev-1");

        assertEquals(2, snapshots.size());
        assertEquals(3, snapshots.get(snapshots.size() - 1).getVersion());
        verify(hotSnapshotService).save(any());
    }

    @Test
    void recoverFallsBackToColdSnapshotWhenHotSnapshotFails() {
        login();
        stubPersistence();
        seedSessionTurnsAndSnapshot();
        InterviewSessionHotSnapshotService hotSnapshotService = mock(InterviewSessionHotSnapshotService.class);
        doThrow(new IllegalStateException("redis down")).when(hotSnapshotService).load(eq("session-1"), eq("user-1"));

        CareerInterviewSessionVO result = newService(hotSnapshotService).recover("session-1");

        assertEquals(2, result.getCurrentTurnNo());
        assertEquals("RUNNING", result.getStatus());
    }

    private InterviewSessionRecoveryServiceImpl newService() {
        return new InterviewSessionRecoveryServiceImpl(sessionMapper, turnMapper, snapshotMapper);
    }

    private InterviewSessionRecoveryServiceImpl newService(InterviewSessionHotSnapshotService hotSnapshotService) {
        return new InterviewSessionRecoveryServiceImpl(sessionMapper, turnMapper, snapshotMapper, hotSnapshotService);
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
