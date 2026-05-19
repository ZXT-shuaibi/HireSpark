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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionSnapshotDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionSnapshotMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.service.flow.InterviewFlowStateMachine;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewSessionRecoveryServiceImpl implements InterviewSessionRecoveryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewSessionSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void snapshotStableState(InterviewSessionDO session, String userId, String lastAppliedStepKey) {
        if (session == null || StrUtil.isBlank(session.getId()) || StrUtil.isBlank(userId)) {
            return;
        }
        List<InterviewTurnDO> turns = listTurns(session.getId(), userId);
        InterviewSessionSnapshotDO latest = latestSnapshot(session.getId(), userId);
        int nextVersion = latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
        InterviewSessionSnapshotDO snapshot = InterviewSessionSnapshotDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .version(nextVersion)
                .snapshotJson(writeJson(buildSnapshotPayload(session, turns),
                        "Failed to serialize interview session snapshot JSON"))
                .lastAppliedStepKey(lastAppliedStepKey)
                .status(session.getStatus())
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        snapshotMapper.insert(snapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewSessionVO recover(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        if (InterviewSessionStatus.CANCELLED.name().equals(session.getStatus())) {
            throw new ClientException("Interview session is already cancelled");
        }
        InterviewSessionSnapshotDO snapshot = latestSnapshot(sessionId, userId);
        List<InterviewTurnDO> turns = listTurns(sessionId, userId);
        Integer recoveredTurnNo = recoverTurnNo(session, snapshot, turns);
        session.setCurrentTurnNo(recoveredTurnNo);
        if (!InterviewSessionStatus.COMPLETED.name().equals(session.getStatus())) {
            InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.RUNNING);
        }
        session.setUpdatedBy(userId);
        sessionMapper.updateById(session);

        InterviewTurnDO currentTurn = turns.stream()
                .filter(turn -> recoveredTurnNo.equals(turn.getTurnNo()))
                .findFirst()
                .orElse(null);
        return CareerInterviewSessionVO.builder()
                .id(session.getId())
                .status(session.getStatus())
                .plan(readMap(session.getPlanJson(), "Failed to parse interview plan JSON"))
                .currentTurnNo(session.getCurrentTurnNo())
                .currentQuestion(currentTurn == null ? null : toTurnVO(currentTurn))
                .build();
    }

    private Map<String, Object> buildSnapshotPayload(InterviewSessionDO session, List<InterviewTurnDO> turns) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("status", session.getStatus());
        payload.put("currentTurnNo", session.getCurrentTurnNo());
        payload.put("turns", turns.stream().map(this::toTurnSnapshot).toList());
        return payload;
    }

    private Map<String, Object> toTurnSnapshot(InterviewTurnDO turn) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("turnNo", turn.getTurnNo());
        item.put("turnType", turn.getTurnType());
        item.put("question", turn.getQuestion());
        item.put("answer", turn.getAnswer());
        item.put("status", turn.getStatus());
        item.put("score", turn.getScore());
        item.put("stepIdempotencyKey", turn.getStepIdempotencyKey());
        item.put("answerStatus", turn.getAnswerStatus());
        item.put("evaluationStatus", turn.getEvaluationStatus());
        item.put("followUpDecisionStatus", turn.getFollowUpDecisionStatus());
        item.put("compensationStatus", turn.getCompensationStatus());
        return item;
    }

    private Integer recoverTurnNo(InterviewSessionDO session,
                                  InterviewSessionSnapshotDO snapshot,
                                  List<InterviewTurnDO> turns) {
        Integer fromSnapshot = snapshot == null ? null : extractInteger(readMap(snapshot.getSnapshotJson(),
                "Failed to parse interview session snapshot JSON").get("currentTurnNo"));
        Integer candidate = fromSnapshot == null ? session.getCurrentTurnNo() : fromSnapshot;
        if (candidate == null || candidate < 1 || turns.stream().noneMatch(turn -> candidate.equals(turn.getTurnNo()))) {
            return turns.stream()
                    .map(InterviewTurnDO::getTurnNo)
                    .filter(turnNo -> turnNo != null && turnNo > 0)
                    .max(Integer::compareTo)
                    .orElse(1);
        }
        return candidate;
    }

    private InterviewSessionDO requireSession(String sessionId, String userId) {
        if (StrUtil.isBlank(sessionId)) {
            throw new ClientException("Interview session id is required");
        }
        InterviewSessionDO session = sessionMapper.selectOne(Wrappers.lambdaQuery(InterviewSessionDO.class)
                .eq(InterviewSessionDO::getId, sessionId)
                .eq(InterviewSessionDO::getUserId, userId)
                .eq(InterviewSessionDO::getDeleted, 0));
        if (session == null) {
            throw new ClientException("Interview session does not exist");
        }
        return session;
    }

    private InterviewSessionSnapshotDO latestSnapshot(String sessionId, String userId) {
        List<InterviewSessionSnapshotDO> snapshots = snapshotMapper.selectList(
                Wrappers.lambdaQuery(InterviewSessionSnapshotDO.class)
                        .eq(InterviewSessionSnapshotDO::getSessionId, sessionId)
                        .eq(InterviewSessionSnapshotDO::getUserId, userId)
                        .eq(InterviewSessionSnapshotDO::getDeleted, 0)
                        .orderByDesc(InterviewSessionSnapshotDO::getVersion)
                        .orderByDesc(InterviewSessionSnapshotDO::getCreateTime)
                        .last("LIMIT 1"));
        return snapshots == null || snapshots.isEmpty() ? null : snapshots.get(0);
    }

    private List<InterviewTurnDO> listTurns(String sessionId, String userId) {
        List<InterviewTurnDO> turns = turnMapper.selectList(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, sessionId)
                .eq(InterviewTurnDO::getUserId, userId)
                .eq(InterviewTurnDO::getDeleted, 0)
                .orderByAsc(InterviewTurnDO::getTurnNo));
        return turns == null ? List.of() : turns;
    }

    private CareerInterviewTurnVO toTurnVO(InterviewTurnDO turn) {
        return CareerInterviewTurnVO.builder()
                .id(turn.getId())
                .sessionId(turn.getSessionId())
                .turnNo(turn.getTurnNo())
                .turnType(turn.getTurnType())
                .question(turn.getQuestion())
                .answer(turn.getAnswer())
                .score(turn.getScore())
                .feedback(readMap(turn.getFeedbackJson(), "Failed to parse interview feedback JSON"))
                .status(turn.getStatus())
                .stepIdempotencyKey(turn.getStepIdempotencyKey())
                .answerStatus(turn.getAnswerStatus())
                .evaluationStatus(turn.getEvaluationStatus())
                .followUpDecisionStatus(turn.getFollowUpDecisionStatus())
                .compensationStatus(turn.getCompensationStatus())
                .attemptCount(turn.getAttemptCount())
                .lastError(turn.getLastError())
                .build();
    }

    private Map<String, Object> readMap(String value, String errorMessage) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    private Integer extractInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }
}
