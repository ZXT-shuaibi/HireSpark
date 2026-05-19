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

package com.nageoffer.ai.ragent.career.service.flow;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.enums.InterviewTurnStatus;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class InterviewFlowStateMachine {

    private static final String SESSION_TRANSITION_INVALID = "面试会话状态流转不合法";
    private static final String TURN_TRANSITION_INVALID = "面试轮次状态流转不合法";

    private static final Map<InterviewSessionStatus, Set<InterviewSessionStatus>> SESSION_TRANSITIONS =
            new EnumMap<>(InterviewSessionStatus.class);

    private static final Map<InterviewTurnStatus, Set<InterviewTurnStatus>> TURN_TRANSITIONS =
            new EnumMap<>(InterviewTurnStatus.class);

    static {
        SESSION_TRANSITIONS.put(InterviewSessionStatus.CREATED, EnumSet.of(
                InterviewSessionStatus.RUNNING,
                InterviewSessionStatus.PAUSED,
                InterviewSessionStatus.COMPLETED,
                InterviewSessionStatus.CANCELLED));
        SESSION_TRANSITIONS.put(InterviewSessionStatus.RUNNING, EnumSet.of(
                InterviewSessionStatus.PAUSED,
                InterviewSessionStatus.RECOVERING,
                InterviewSessionStatus.COMPLETED,
                InterviewSessionStatus.CANCELLED));
        SESSION_TRANSITIONS.put(InterviewSessionStatus.PAUSED, EnumSet.of(
                InterviewSessionStatus.RUNNING,
                InterviewSessionStatus.RECOVERING,
                InterviewSessionStatus.COMPLETED,
                InterviewSessionStatus.CANCELLED));
        SESSION_TRANSITIONS.put(InterviewSessionStatus.RECOVERING, EnumSet.of(
                InterviewSessionStatus.RUNNING,
                InterviewSessionStatus.PAUSED,
                InterviewSessionStatus.COMPLETED,
                InterviewSessionStatus.CANCELLED));
        SESSION_TRANSITIONS.put(InterviewSessionStatus.COMPLETED, EnumSet.noneOf(InterviewSessionStatus.class));
        SESSION_TRANSITIONS.put(InterviewSessionStatus.CANCELLED, EnumSet.noneOf(InterviewSessionStatus.class));

        TURN_TRANSITIONS.put(InterviewTurnStatus.ASKED, EnumSet.of(InterviewTurnStatus.ANSWERED));
        TURN_TRANSITIONS.put(InterviewTurnStatus.ANSWERED, EnumSet.of(
                InterviewTurnStatus.EVALUATED,
                InterviewTurnStatus.WAITING_RETRY));
        TURN_TRANSITIONS.put(InterviewTurnStatus.WAITING_RETRY, EnumSet.of(
                InterviewTurnStatus.EVALUATED,
                InterviewTurnStatus.WAITING_RETRY));
        TURN_TRANSITIONS.put(InterviewTurnStatus.EVALUATED, EnumSet.noneOf(InterviewTurnStatus.class));
    }

    private InterviewFlowStateMachine() {
    }

    /**
     * 校验并写入面试会话目标状态，同状态流转视为幂等成功。
     */
    public static void applySessionStatus(InterviewSessionDO session, InterviewSessionStatus target) {
        if (session == null || target == null) {
            throw new ClientException(SESSION_TRANSITION_INVALID);
        }
        InterviewSessionStatus current = parseSessionStatus(session.getStatus());
        if (current == target) {
            session.setStatus(target.name());
            return;
        }
        if (!SESSION_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new ClientException(SESSION_TRANSITION_INVALID);
        }
        session.setStatus(target.name());
    }

    /**
     * 校验并写入面试轮次目标状态，同状态流转视为幂等成功。
     */
    public static void applyTurnStatus(InterviewTurnDO turn, InterviewTurnStatus target) {
        if (turn == null || target == null) {
            throw new ClientException(TURN_TRANSITION_INVALID);
        }
        InterviewTurnStatus current = parseTurnStatus(turn.getStatus());
        if (current == null) {
            if (target != InterviewTurnStatus.ASKED) {
                throw new ClientException(TURN_TRANSITION_INVALID);
            }
            turn.setStatus(target.name());
            return;
        }
        if (current == target) {
            turn.setStatus(target.name());
            return;
        }
        if (!TURN_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new ClientException(TURN_TRANSITION_INVALID);
        }
        turn.setStatus(target.name());
    }

    /**
     * 解析会话状态，空状态按创建态处理。
     */
    private static InterviewSessionStatus parseSessionStatus(String value) {
        if (StrUtil.isBlank(value)) {
            return InterviewSessionStatus.CREATED;
        }
        try {
            return InterviewSessionStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ClientException("面试会话状态不存在");
        }
    }

    /**
     * 解析轮次状态，空状态表示尚未进入首个合法状态。
     */
    private static InterviewTurnStatus parseTurnStatus(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return InterviewTurnStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ClientException("面试轮次状态不存在");
        }
    }
}
