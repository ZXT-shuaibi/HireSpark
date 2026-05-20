package com.nageoffer.ai.ragent.career.service.recovery;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionSnapshotDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnArchiveDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionSnapshotMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnArchiveMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.service.flow.InterviewFlowStateMachine;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class InterviewSessionRecoveryServiceImpl implements InterviewSessionRecoveryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int SNAPSHOT_CAS_RETRY_TIMES = 3;
    private static final long SNAPSHOT_CAS_RETRY_BASE_SLEEP_MILLIS = 20L;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewSessionSnapshotMapper snapshotMapper;
    private final InterviewTurnArchiveMapper turnArchiveMapper;
    private final InterviewSessionHotSnapshotService hotSnapshotService;
    private final InterviewHotSnapshotRefreshCoordinator hotRefreshCoordinator;
    private final InterviewSnapshotMonotonicGuard monotonicGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConversationMemoryService conversationMemoryService;

    public InterviewSessionRecoveryServiceImpl(InterviewSessionMapper sessionMapper,
                                               InterviewTurnMapper turnMapper,
                                               InterviewSessionSnapshotMapper snapshotMapper) {
        this(sessionMapper, turnMapper, snapshotMapper, null, null, null, new InterviewSnapshotMonotonicGuard());
    }

    public InterviewSessionRecoveryServiceImpl(InterviewSessionMapper sessionMapper,
                                               InterviewTurnMapper turnMapper,
                                               InterviewSessionSnapshotMapper snapshotMapper,
                                               InterviewSessionHotSnapshotService hotSnapshotService) {
        this(sessionMapper, turnMapper, snapshotMapper, null, hotSnapshotService, null, new InterviewSnapshotMonotonicGuard());
    }

    @Autowired
    public InterviewSessionRecoveryServiceImpl(InterviewSessionMapper sessionMapper,
                                               InterviewTurnMapper turnMapper,
                                               InterviewSessionSnapshotMapper snapshotMapper,
                                               InterviewTurnArchiveMapper turnArchiveMapper,
                                               InterviewSessionHotSnapshotService hotSnapshotService,
                                               InterviewHotSnapshotRefreshCoordinator hotRefreshCoordinator,
                                               InterviewSnapshotMonotonicGuard monotonicGuard) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.snapshotMapper = snapshotMapper;
        this.turnArchiveMapper = turnArchiveMapper;
        this.hotSnapshotService = hotSnapshotService;
        this.hotRefreshCoordinator = hotRefreshCoordinator;
        this.monotonicGuard = monotonicGuard == null ? new InterviewSnapshotMonotonicGuard() : monotonicGuard;
    }

    @Autowired(required = false)
    public void setConversationMemoryService(ConversationMemoryService conversationMemoryService) {
        this.conversationMemoryService = conversationMemoryService;
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
        for (int attempt = 1; attempt <= SNAPSHOT_CAS_RETRY_TIMES; attempt++) {
            InterviewSessionSnapshotDO latest = latestSnapshot(session.getId(), userId);
            if (monotonicGuard.alreadyApplied(latest, lastAppliedStepKey)) {
                scheduleHotSnapshotSave(buildHotSnapshot(latest, session, turns, userId), false);
                return;
            }
            InterviewSnapshotMonotonicGuard.SnapshotMetrics metrics =
                    monotonicGuard.guard(latest, snapshotMetrics(turns));
            int nextVersion = latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
            int nextMaterialVersion = latest == null || latest.getMaterialVersion() == null
                    ? 1
                    : latest.getMaterialVersion() + 1;
            InterviewSessionSnapshotDO snapshot = buildColdSnapshot(session,
                    userId,
                    lastAppliedStepKey,
                    turns,
                    metrics,
                    nextVersion,
                    nextMaterialVersion);
            ensureSnapshotId(snapshot);
            int inserted = snapshotMapper.insertIfVersionAbsent(snapshot);
            if (inserted > 0) {
                archiveTurns(session, userId, turns, snapshot);
                scheduleHotSnapshotSave(buildHotSnapshot(snapshot, session, turns, userId),
                        shouldForceHotRefresh(session));
                return;
            }
            if (attempt >= SNAPSHOT_CAS_RETRY_TIMES) {
                throw new ServiceException("Interview snapshot CAS retry exhausted");
            }
            sleepBeforeSnapshotRetry(attempt);
        }
    }

    /**
     * 从热快照或冷快照恢复完整面试运行态。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewSessionVO recover(String sessionId) {
        return recover(sessionId, InterviewRecoveryScope.FULL_RUNTIME);
    }

    /**
     * 按指定范围恢复面试运行态，支持轻量流程恢复和完整热态恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewSessionVO recover(String sessionId, InterviewRecoveryScope scope) {
        InterviewRecoveryScope recoveryScope = InterviewRecoveryScope.resolve(scope);
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        if (InterviewSessionStatus.CANCELLED.name().equals(session.getStatus())) {
            throw new ClientException("Interview session is already cancelled");
        }
        SnapshotCandidate snapshot = selectRecoverySnapshot(latestSnapshot(sessionId, userId),
                recoveryScope.canReadHotSnapshot() ? loadHotSnapshot(sessionId, userId) : Optional.empty());
        List<InterviewTurnDO> turns = recoveryScope.canReadPlayback() ? listTurns(sessionId, userId) : List.of();
        Integer recoveredTurnNo = recoverTurnNo(session, snapshot, turns, recoveryScope);
        session.setCurrentTurnNo(recoveredTurnNo);
        if (InterviewSessionStatus.COMPLETED.name().equals(snapshotStatus(snapshot))) {
            InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.COMPLETED);
        } else if (!InterviewSessionStatus.COMPLETED.name().equals(session.getStatus())) {
            InterviewFlowStateMachine.applySessionStatus(session, InterviewSessionStatus.RUNNING);
        }
        session.setUpdatedBy(userId);
        sessionMapper.updateById(session);
        scheduleRecoveryMemoryCompression(sessionId, userId);

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

    private void triggerRecoveryMemoryCompression(String sessionId, String userId) {
        if (conversationMemoryService == null) {
            return;
        }
        try {
            conversationMemoryService.compressOnRecoveryEvent(sessionId, userId);
        } catch (RuntimeException ex) {
            log.warn("面试恢复后触发会话记忆压缩失败，sessionId={}，原因={}", sessionId, ex.getMessage());
        }
    }

    private void scheduleRecoveryMemoryCompression(String sessionId, String userId) {
        if (conversationMemoryService == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    triggerRecoveryMemoryCompression(sessionId, userId);
                }
            });
            return;
        }
        triggerRecoveryMemoryCompression(sessionId, userId);
    }

    /**
     * 构建冷快照实体，集中保存版本、水位和幂等字段。
     */
    private InterviewSessionSnapshotDO buildColdSnapshot(InterviewSessionDO session,
                                                         String userId,
                                                         String lastAppliedStepKey,
                                                         List<InterviewTurnDO> turns,
                                                         InterviewSnapshotMonotonicGuard.SnapshotMetrics metrics,
                                                         int nextVersion,
                                                         int nextMaterialVersion) {
        Map<String, Object> payload = buildSnapshotPayload(session, turns, metrics, nextMaterialVersion);
        String snapshotJson = writeJson(payload, "Failed to serialize interview session snapshot JSON");
        return InterviewSessionSnapshotDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .version(nextVersion)
                .materialVersion(nextMaterialVersion)
                .snapshotJson(snapshotJson)
                .lastAppliedStepKey(lastAppliedStepKey)
                .lastMutationId(lastAppliedStepKey)
                .lastTurnSeq(metrics.lastTurnSeq())
                .archiveWatermark(metrics.archiveWatermark())
                .scoreCount(metrics.scoreCount())
                .lastCommittedTurnDigest(lastCommittedTurnDigest(turns))
                .status(session.getStatus())
                .createdBy(userId)
                .updatedBy(userId)
                .build();
    }

    /**
     * 构建可审计和可回放的面试会话快照载荷。
     */
    private Map<String, Object> buildSnapshotPayload(InterviewSessionDO session,
                                                     List<InterviewTurnDO> turns,
                                                     InterviewSnapshotMonotonicGuard.SnapshotMetrics metrics,
                                                     Integer materialVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("status", session.getStatus());
        payload.put("currentTurnNo", session.getCurrentTurnNo());
        payload.put("materialVersion", materialVersion);
        payload.put("lastTurnSeq", metrics.lastTurnSeq());
        payload.put("archiveWatermark", metrics.archiveWatermark());
        payload.put("scoreCount", metrics.scoreCount());
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
     * 将面试轮次按快照版本追加到不可变归档表。
     */
    private void archiveTurns(InterviewSessionDO session,
                              String userId,
                              List<InterviewTurnDO> turns,
                              InterviewSessionSnapshotDO snapshot) {
        if (turnArchiveMapper == null || turns == null || turns.isEmpty()) {
            return;
        }
        for (InterviewTurnDO turn : turns) {
            if (!shouldArchiveTurn(turn)) {
                continue;
            }
            InterviewTurnArchiveDO archive = InterviewTurnArchiveDO.builder()
                    .sessionId(session.getId())
                    .userId(userId)
                    .requestId(StrUtil.blankToDefault(turn.getStepIdempotencyKey(),
                            session.getId() + ":" + turn.getTurnNo()))
                    .seq(turn.getTurnNo())
                    .snapshotVersion(snapshot.getVersion())
                    .turnPayloadJson(writeJson(buildTurnArchivePayload(session, turns, turn),
                            "Failed to serialize interview turn archive JSON"))
                    .turnDigest(turnDigest(turn))
                    .build();
            ensureArchiveId(archive);
            turnArchiveMapper.insertIfAbsent(archive);
        }
    }

    /**
     * 判断轮次是否值得进入回放归档。
     */
    private boolean shouldArchiveTurn(InterviewTurnDO turn) {
        if (turn == null || turn.getTurnNo() == null || turn.getTurnNo() <= 0) {
            return false;
        }
        return StrUtil.isNotBlank(turn.getAnswer())
                || turn.getScore() != null
                || StrUtil.isNotBlank(turn.getFeedbackJson())
                || "EVALUATED".equals(turn.getStatus());
    }

    /**
     * 构建轮次归档载荷，保留回放所需的请求、题号、答案、评分和下一题信息。
     */
    private Map<String, Object> buildTurnArchivePayload(InterviewSessionDO session,
                                                        List<InterviewTurnDO> turns,
                                                        InterviewTurnDO turn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", StrUtil.blankToDefault(turn.getStepIdempotencyKey(),
                session.getId() + ":" + turn.getTurnNo()));
        payload.put("questionNumber", turn.getTurnNo());
        payload.put("answerContent", turn.getAnswer());
        payload.put("score", turn.getScore());
        payload.put("totalScore", scoreCount(turns));
        payload.put("followUpNeeded", "FOLLOW_UP_REQUIRED".equals(turn.getFollowUpDecisionStatus()));
        payload.put("isFollowUp", "FOLLOW_UP".equals(turn.getTurnType()));
        payload.put("followUpCount", followUpCount(turns));
        payload.put("nextQuestionNumber", nextTurn(turns, turn).map(InterviewTurnDO::getTurnNo).orElse(null));
        payload.put("nextQuestion", nextTurn(turns, turn).map(InterviewTurnDO::getQuestion).orElse(null));
        payload.put("finished", InterviewSessionStatus.COMPLETED.name().equals(session.getStatus())
                || "EVALUATED".equals(turn.getStatus()));
        return payload;
    }

    /**
     * 计算当前轮次之后的下一轮，用于回放时串联问答上下文。
     */
    private Optional<InterviewTurnDO> nextTurn(List<InterviewTurnDO> turns, InterviewTurnDO current) {
        if (current.getTurnNo() == null) {
            return Optional.empty();
        }
        return turns.stream()
                .filter(turn -> turn.getTurnNo() != null && turn.getTurnNo() > current.getTurnNo())
                .min((left, right) -> Integer.compare(left.getTurnNo(), right.getTurnNo()));
    }

    /**
     * 统计追问轮次数量，便于恢复后保持追问上限判断一致。
     */
    private int followUpCount(List<InterviewTurnDO> turns) {
        return Math.toIntExact(turns.stream()
                .filter(turn -> "FOLLOW_UP".equals(turn.getTurnType()))
                .count());
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
                .materialVersion(snapshot.getMaterialVersion())
                .status(session.getStatus())
                .currentTurnNo(session.getCurrentTurnNo())
                .lastAppliedStepKey(snapshot.getLastAppliedStepKey())
                .lastMutationId(snapshot.getLastMutationId())
                .lastTurnSeq(snapshot.getLastTurnSeq())
                .archiveWatermark(snapshot.getArchiveWatermark())
                .scoreCount(snapshot.getScoreCount())
                .lastCommittedTurnDigest(snapshot.getLastCommittedTurnDigest())
                .snapshotJson(snapshot.getSnapshotJson())
                .build();
    }

    /**
     * 将 Redis 热快照刷新延迟到事务提交后，避免回滚后热态领先冷态。
     */
    private void scheduleHotSnapshotSave(InterviewSessionHotSnapshotService.HotSnapshot hotSnapshot, boolean forceFlush) {
        if (hotSnapshotService == null || hotSnapshot == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saveHotSnapshot(hotSnapshot, forceFlush);
                }
            });
            return;
        }
        saveHotSnapshot(hotSnapshot, forceFlush);
    }

    /**
     * 刷新 Redis 热快照，失败时只记录日志。
     */
    private void saveHotSnapshot(InterviewSessionHotSnapshotService.HotSnapshot hotSnapshot, boolean forceFlush) {
        if (hotRefreshCoordinator != null) {
            hotRefreshCoordinator.refresh(hotSnapshot, forceFlush);
            return;
        }
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
     * 计算可恢复题号，保证题号不倒退或指向不存在的轮次。
     */
    private Integer recoverTurnNo(InterviewSessionDO session,
                                  SnapshotCandidate snapshot,
                                  List<InterviewTurnDO> turns,
                                  InterviewRecoveryScope scope) {
        Integer fromSnapshot = snapshot == null ? null : snapshot.currentTurnNo();
        Integer candidate = maxPositive(session.getCurrentTurnNo(), fromSnapshot);
        if (!scope.canReadPlayback()) {
            return candidate == null ? 1 : candidate;
        }
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
     * 查询会话下所有面试轮次。
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
     * 从当前轮次列表提取需要单调保护的运行态指标。
     */
    private InterviewSnapshotMonotonicGuard.SnapshotMetrics snapshotMetrics(List<InterviewTurnDO> turns) {
        int lastTurnSeq = maxTurnNo(turns);
        return new InterviewSnapshotMonotonicGuard.SnapshotMetrics(lastTurnSeq, lastTurnSeq, scoreCount(turns));
    }

    /**
     * 计算最后一个已提交轮次的摘要，用于恢复回放时识别重复或乱序提交。
     */
    private String lastCommittedTurnDigest(List<InterviewTurnDO> turns) {
        return turns.stream()
                .filter(turn -> turn.getTurnNo() != null)
                .max((left, right) -> Integer.compare(left.getTurnNo(), right.getTurnNo()))
                .map(this::turnDigest)
                .orElse(null);
    }

    /**
     * 计算轮次摘要，避免完整答案进入幂等校验字段。
     */
    private String turnDigest(InterviewTurnDO turn) {
        String answer = StrUtil.blankToDefault(turn.getAnswer(), "");
        String digestSource = turn.getTurnNo() + ":" + StrUtil.subPre(answer, 256);
        return sha256(digestSource);
    }

    /**
     * 计算 SHA-256 摘要。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException("Failed to build interview turn digest");
        }
    }

    /**
     * 判断当前热快照是否需要绕过去抖立即刷新。
     */
    private boolean shouldForceHotRefresh(InterviewSessionDO session) {
        return InterviewSessionStatus.COMPLETED.name().equals(session.getStatus());
    }

    /**
     * 确保自定义插入 SQL 具备主键。
     */
    private void ensureSnapshotId(InterviewSessionSnapshotDO snapshot) {
        if (StrUtil.isBlank(snapshot.getId())) {
            snapshot.setId(IdWorker.getIdStr());
        }
    }

    /**
     * 确保归档自定义插入 SQL 具备主键。
     */
    private void ensureArchiveId(InterviewTurnArchiveDO archive) {
        if (StrUtil.isBlank(archive.getId())) {
            archive.setId(IdWorker.getIdStr());
        }
    }

    /**
     * CAS 版本冲突后做短暂退避，降低高并发补偿时的重复冲突。
     */
    private void sleepBeforeSnapshotRetry(int attempt) {
        try {
            Thread.sleep(SNAPSHOT_CAS_RETRY_BASE_SLEEP_MILLIS * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Interview snapshot retry interrupted");
        }
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
