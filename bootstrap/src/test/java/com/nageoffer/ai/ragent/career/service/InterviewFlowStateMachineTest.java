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

import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.enums.InterviewTurnStatus;
import com.nageoffer.ai.ragent.career.service.flow.InterviewFlowStateMachine;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFlowStateMachineTest {

    /**
     * 校验会话状态机允许显式合法流转并写入目标状态。
     */
    @Test
    void sessionAllowsLegalTransitionAndWritesTargetStatus() {
        InterviewSessionDO session = InterviewSessionDO.builder()
                .status(InterviewSessionStatus.CREATED.name())
                .build();

        InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.RUNNING);

        assertEquals(InterviewSessionStatus.RUNNING.name(), session.getStatus());
    }

    /**
     * 校验会话终态不能回退到运行态。
     */
    @Test
    void sessionRejectsIllegalTransitionFromTerminalStatus() {
        InterviewSessionDO session = InterviewSessionDO.builder()
                .status(InterviewSessionStatus.COMPLETED.name())
                .build();

        assertThrows(ClientException.class,
                () -> InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.RUNNING));
        assertEquals(InterviewSessionStatus.COMPLETED.name(), session.getStatus());
    }

    /**
     * 校验会话同状态流转可以作为幂等操作重复执行。
     */
    @Test
    void sessionAllowsIdempotentSameStatusTransition() {
        InterviewSessionDO session = InterviewSessionDO.builder()
                .status(InterviewSessionStatus.RUNNING.name())
                .build();

        InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.RUNNING);

        assertEquals(InterviewSessionStatus.RUNNING.name(), session.getStatus());
    }

    /**
     * 校验轮次状态机允许从提问态进入已答态。
     */
    @Test
    void turnAllowsLegalTransitionAndWritesTargetStatus() {
        InterviewTurnDO turn = InterviewTurnDO.builder()
                .status(InterviewTurnStatus.ASKED.name())
                .build();

        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.ANSWERED);

        assertEquals(InterviewTurnStatus.ANSWERED.name(), turn.getStatus());
    }

    /**
     * 校验轮次状态机拒绝从已评分态回退到已答态。
     */
    @Test
    void turnRejectsIllegalTransitionFromEvaluatedToAnswered() {
        InterviewTurnDO turn = InterviewTurnDO.builder()
                .status(InterviewTurnStatus.EVALUATED.name())
                .build();

        assertThrows(ClientException.class,
                () -> InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.ANSWERED));
        assertEquals(InterviewTurnStatus.EVALUATED.name(), turn.getStatus());
    }

    /**
     * 校验已评分轮次不能回退到等待补偿重试状态。
     */
    @Test
    void turnRejectsIllegalTransitionFromEvaluatedToWaitingRetry() {
        InterviewTurnDO turn = InterviewTurnDO.builder()
                .status(InterviewTurnStatus.EVALUATED.name())
                .build();

        assertThrows(ClientException.class,
                () -> InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.WAITING_RETRY));
        assertEquals(InterviewTurnStatus.EVALUATED.name(), turn.getStatus());
    }

    /**
     * 校验轮次同状态流转可以作为幂等操作重复执行。
     */
    @Test
    void turnAllowsIdempotentSameStatusTransition() {
        InterviewTurnDO turn = InterviewTurnDO.builder()
                .status(InterviewTurnStatus.WAITING_RETRY.name())
                .build();

        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.WAITING_RETRY);

        assertEquals(InterviewTurnStatus.WAITING_RETRY.name(), turn.getStatus());
    }
}
