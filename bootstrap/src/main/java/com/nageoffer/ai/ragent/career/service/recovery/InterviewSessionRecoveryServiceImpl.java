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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class InterviewSessionRecoveryServiceImpl implements InterviewSessionRecoveryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewSessionSnapshotMapper snapshotMapper;
    private final InterviewSessionHotSnapshotService hotSnapshotService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InterviewSessionRecoveryServiceImpl(InterviewSessionMapper sessionMapper,
                                               InterviewTurnMapper turnMapper,
                                               InterviewSessionSnapshotMapper snapshotMapper) {
        this(sessionMapper, turnMapper, snapshotMapper, null);
    }

    @Autowired
    public InterviewSessionRecoveryServiceImpl(InterviewSessionMapper sessionMapper,
                                               InterviewTurnMapper turnMapper,
                                               InterviewSessionSnapshotMapper snapshotMapper,
                                               InterviewSessionHotSnapshotService hotSnapshotService) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.snapshotMapper = snapshotMapper;
        this.hotSnapshotService = hotSnapshotService;
    }

    /**
     * 持久化面试冷快照，并在冷快照成功后刷新 Redis 热快照。
     */
    @Override
    public void snapshotStableState(InterviewSessionDO session, String userId, String lastAppliedStepKey) {
        if (session == null || StrUtil.isBlank(session.getId()) || StrUtil.isBlank(userId)) {
            return;
        }
        List<InterviewTurnDO> turns = listTurns(session.getId(), userId);
        InterviewSessionSnapshotDO latest = latestSnapshot(session.getId(), userId);
        int nextVersion = latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
        Map<String, Object> payload = buildSnapshotPayload(session, turns);
        String snapshotJson = writeJson(payload, "Failed to serialize interview session snapshot JSON");
        InterviewSessionSnapshotDO snapshot = InterviewSessionSnapshotDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .version(nextVersion)
                .snapshotJson(snapshotJson)
                .lastAppliedStepKey(lastAppliedStepKey)
                .status(session.getStatus())
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        snapshotMapper.insert(snapshot);
        scheduleHotSnapshotSave(buildHotSnapshot(snapshot, session, turns, userId));
    }

    /**
     * 从热快照或冷快照恢复面试会话运行态，恢复时保护当前题号不倒退。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewSessionVO recover(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        if (InterviewSessionStatus.CANCELLED.name().equals(session.getStatus())) {
            throw new ClientException("Interview session is already cancelled");
        }
        SnapshotCandidate snapshot = selectRecoverySnapshot(latestSnapshot(sessionId, userId),
                loadHotSnapshot(sessionId, userId));
        List<InterviewTurnDO> turns = listTurns(sessionId, userId);
        Integer recoveredTurnNo = recoverTurnNo(session, snapshot, turns);
        session.setCurrentTurnNo(recoveredTurnNo);
        if (InterviewSessionStatus.COMPLETED.name().equals(snapshotStatus(snapshot))) {
            InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.COMPLETED);
        } else if (!InterviewSessionStatus.COMPLETED.name().equals(session.getStatus())) {
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

    /**
     * 构建可审计和可回放的面试会话快照载荷。
     */
    private Map<String, Object> buildSnapshotPayload(InterviewSessionDO session, List<InterviewTurnDO> turns) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("status", session.getStatus());
        payload.put("currentTurnNo", session.getCurrentTurnNo());
        payload.put("turns", turns.stream().map(this::toTurnSnapshot).toList());
        return payload;
    }

    /**
     * 构建单个面试轮次在快照中的不可变摘要。
     */
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

    /**
     * 根据冷快照和轮次归档构建 Redis 热快照对象。
     */
    private InterviewSessionHotSnapshotService.HotSnapshot buildHotSnapshot(InterviewSessionSnapshotDO snapshot,
                                                                            InterviewSessionDO session,
                                                                            List<InterviewTurnDO> turns,
                                                                            String userId) {
        return InterviewSessionHotSnapshotService.HotSnapshot.builder()
                .sessionId(session.getId())
                .userId(userId)
                .version(snapshot.getVersion())
                .status(session.getStatus())
                .currentTurnNo(session.getCurrentTurnNo())
                .lastAppliedStepKey(snapshot.getLastAppliedStepKey())
                .lastTurnSeq(maxTurnNo(turns))
                .archiveWatermark(maxTurnNo(turns))
                .scoreCount(scoreCount(turns))
                .snapshotJson(snapshot.getSnapshotJson())
                .build();
    }

    /**
     * 将 Redis 热快照刷新延迟到数据库事务提交后，避免回滚后热态领先冷态。
     */
    private void scheduleHotSnapshotSave(InterviewSessionHotSnapshotService.HotSnapshot hotSnapshot) {
        if (hotSnapshotService == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saveHotSnapshot(hotSnapshot);
                }
            });
            return;
        }
        saveHotSnapshot(hotSnapshot);
    }

    /**
     * 刷新 Redis 热快照，失败时只记录日志。
     */
    private void saveHotSnapshot(InterviewSessionHotSnapshotService.HotSnapshot hotSnapshot) {
        try {
            hotSnapshotService.save(hotSnapshot);
        } catch (RuntimeException ex) {
            log.warn("面试热快照写入失败，已保留冷快照，sessionId={}，原因={}", hotSnapshot.getSessionId(), ex.getMessage());
        }
    }

    /**
     * 安全读取 Redis 热快照，Redis 异常时回落到冷快照。
     */
    private Optional<InterviewSessionHotSnapshotService.HotSnapshot> loadHotSnapshot(String sessionId, String userId) {
        if (hotSnapshotService == null) {
            return Optional.empty();
        }
        try {
            return hotSnapshotService.load(sessionId, userId);
        } catch (RuntimeException ex) {
            log.warn("面试热快照读取失败，开始使用冷快照恢复，sessionId={}，原因={}", sessionId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 选择恢复快照，热快照版本高于冷快照时优先使用热快照。
     */
    private SnapshotCandidate selectRecoverySnapshot(InterviewSessionSnapshotDO coldSnapshot,
                                                     Optional<InterviewSessionHotSnapshotService.HotSnapshot> hotSnapshot) {
        SnapshotCandidate cold = coldSnapshot == null ? null : SnapshotCandidate.fromCold(coldSnapshot);
        SnapshotCandidate hot = hotSnapshot.map(SnapshotCandidate::fromHot).orElse(null);
        if (hot == null) {
            return cold;
        }
        if (cold == null || versionOf(hot) > versionOf(cold)) {
            return hot;
        }
        return cold;
    }

    /**
     * 按照归档轮次和快照候选计算可恢复题号，保证题号不会倒退或指向不存在的轮次。
     */
    private Integer recoverTurnNo(InterviewSessionDO session,
                                  SnapshotCandidate snapshot,
                                  List<InterviewTurnDO> turns) {
        Integer fromSnapshot = snapshot == null ? null : snapshot.currentTurnNo();
        Integer candidate = maxPositive(session.getCurrentTurnNo(), fromSnapshot);
        if (candidate == null || candidate < 1 || turns.stream().noneMatch(turn -> candidate.equals(turn.getTurnNo()))) {
            return turns.stream()
                    .map(InterviewTurnDO::getTurnNo)
                    .filter(turnNo -> turnNo != null && turnNo > 0)
                    .max(Integer::compareTo)
                    .orElse(1);
        }
        return candidate;
    }

    /**
     * 查询当前用户可恢复的面试会话。
     */
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

    /**
     * 查询最新一条 PostgreSQL 冷快照。
     */
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

    /**
     * 查询会话下所有已归档面试轮次。
     */
    private List<InterviewTurnDO> listTurns(String sessionId, String userId) {
        List<InterviewTurnDO> turns = turnMapper.selectList(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, sessionId)
                .eq(InterviewTurnDO::getUserId, userId)
                .eq(InterviewTurnDO::getDeleted, 0)
                .orderByAsc(InterviewTurnDO::getTurnNo));
        return turns == null ? List.of() : turns;
    }

    /**
     * 将面试轮次实体转换为前端会话视图。
     */
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

    /**
     * 将 JSON 字符串解析为 Map。
     */
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

    /**
     * 将对象序列化为 JSON 字符串。
     */
    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException(errorMessage);
        }
    }

    /**
     * 获取当前登录用户 ID。
     */
    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }

    /**
     * 统计已经持久化的最大面试轮次号。
     */
    private Integer maxTurnNo(List<InterviewTurnDO> turns) {
        return turns.stream()
                .map(InterviewTurnDO::getTurnNo)
                .filter(turnNo -> turnNo != null && turnNo > 0)
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * 统计已有评分结果的面试轮次数。
     */
    private Integer scoreCount(List<InterviewTurnDO> turns) {
        return Math.toIntExact(turns.stream()
                .filter(turn -> turn.getScore() != null)
                .count());
    }

    /**
     * 返回两个正整数中的最大值。
     */
    private Integer maxPositive(Integer left, Integer right) {
        Integer result = null;
        if (left != null && left > 0) {
            result = left;
        }
        if (right != null && right > 0) {
            result = result == null ? right : Math.max(result, right);
        }
        return result;
    }

    /**
     * 返回快照状态，缺失时返回空字符串。
     */
    private String snapshotStatus(SnapshotCandidate snapshot) {
        return snapshot == null ? "" : StrUtil.blankToDefault(snapshot.status(), "");
    }

    /**
     * 返回快照版本，缺失时按 0 处理。
     */
    private int versionOf(SnapshotCandidate snapshot) {
        return snapshot.version() == null ? 0 : snapshot.version();
    }

    private record SnapshotCandidate(Integer version,
                                     String status,
                                     Integer currentTurnNo) {

        /**
         * 将 PostgreSQL 冷快照转换为统一恢复候选。
         */
        private static SnapshotCandidate fromCold(InterviewSessionSnapshotDO snapshot) {
            Map<String, Object> payload = readSnapshotPayload(snapshot.getSnapshotJson());
            return new SnapshotCandidate(snapshot.getVersion(),
                    StrUtil.blankToDefault(snapshot.getStatus(), stringValue(payload.get("status"))),
                    extractIntegerValue(payload.get("currentTurnNo")));
        }

        /**
         * 将 Redis 热快照转换为统一恢复候选。
         */
        private static SnapshotCandidate fromHot(InterviewSessionHotSnapshotService.HotSnapshot snapshot) {
            Map<String, Object> payload = readSnapshotPayload(snapshot.getSnapshotJson());
            Integer currentTurnNo = snapshot.getCurrentTurnNo() == null
                    ? extractIntegerValue(payload.get("currentTurnNo"))
                    : snapshot.getCurrentTurnNo();
            return new SnapshotCandidate(snapshot.getVersion(),
                    StrUtil.blankToDefault(snapshot.getStatus(), stringValue(payload.get("status"))),
                    currentTurnNo);
        }

        /**
         * 解析快照载荷，解析失败时返回空 Map 让恢复逻辑继续兜底。
         */
        private static Map<String, Object> readSnapshotPayload(String snapshotJson) {
            if (StrUtil.isBlank(snapshotJson)) {
                return Map.of();
            }
            try {
                return new ObjectMapper().readValue(snapshotJson, MAP_TYPE);
            } catch (Exception ignored) {
                return Map.of();
            }
        }

        /**
         * 从动态类型中解析整数。
         */
        private static Integer extractIntegerValue(Object value) {
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

        /**
         * 从动态类型中读取字符串。
         */
        private static String stringValue(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }
}
