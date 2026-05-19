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

package com.nageoffer.ai.ragent.career.service.runtime;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.enums.InterviewRuntimeStatus;
import com.nageoffer.ai.ragent.career.enums.InterviewTurnStatus;
import com.nageoffer.ai.ragent.career.service.flow.InterviewFlowStateMachine;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class InterviewTurnRuntimeServiceImpl implements InterviewTurnRuntimeService {

    /**
     * 根据会话、轮次和答案内容构造答题步骤幂等键。
     */
    @Override
    public String buildStepIdempotencyKey(String sessionId, Integer turnNo, String answerRevision, String answer) {
        String revision = StrUtil.isBlank(answerRevision) ? sha256(answer) : answerRevision.trim();
        return sessionId + ":" + turnNo + ":" + revision;
    }

    /**
     * 判断当前轮次是否已经处理过同一个幂等步骤。
     */
    @Override
    public boolean isSameStep(InterviewTurnDO turn, String stepIdempotencyKey) {
        return turn != null && StrUtil.isNotBlank(stepIdempotencyKey)
                && stepIdempotencyKey.equals(turn.getStepIdempotencyKey());
    }

    /**
     * 初始化一个待作答的面试轮次。
     */
    @Override
    public void initializeAskedTurn(InterviewTurnDO turn) {
        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.ASKED);
        turn.setAnswerStatus(InterviewRuntimeStatus.WAITING_ANSWER.name());
        turn.setEvaluationStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.NONE.name());
        turn.setAttemptCount(0);
        turn.setLastError(null);
    }

    /**
     * 标记候选人答案已保存，并绑定本次答题幂等键。
     */
    @Override
    public void markAnswerSaved(InterviewTurnDO turn, String answer, String stepIdempotencyKey) {
        turn.setAnswer(answer);
        turn.setStepIdempotencyKey(stepIdempotencyKey);
        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.ANSWERED);
        turn.setAnswerStatus(InterviewRuntimeStatus.ANSWER_SAVED.name());
        turn.setEvaluationStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.NONE.name());
        turn.setLastError(null);
    }

    /**
     * 标记轮次进入评分中，并累加评分尝试次数。
     */
    @Override
    public void markEvaluating(InterviewTurnDO turn) {
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATING.name());
        turn.setAttemptCount((turn.getAttemptCount() == null ? 0 : turn.getAttemptCount()) + 1);
        turn.setLastError(null);
    }

    /**
     * 标记轮次评分完成。
     */
    @Override
    public void markEvaluated(InterviewTurnDO turn) {
        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.EVALUATED);
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.NONE.name());
        turn.setLastError(null);
    }

    /**
     * 标记补偿评分已经成功完成。
     */
    @Override
    public void markEvaluationCompensated(InterviewTurnDO turn) {
        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.EVALUATED);
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.COMPENSATED.name());
        turn.setLastError(null);
    }

    /**
     * 标记补偿任务已经被当前实例抢占。
     */
    @Override
    public void markEvaluationRetryClaimed(InterviewTurnDO turn) {
        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.WAITING_RETRY);
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATING.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.COMPENSATING.name());
    }

    /**
     * 标记评分失败并进入等待补偿重试状态。
     */
    @Override
    public void markEvaluationFailed(InterviewTurnDO turn, RuntimeException ex) {
        InterviewFlowStateMachine.applyTurnStatus(turn, InterviewTurnStatus.WAITING_RETRY);
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATION_FAILED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.COMPENSATING.name());
        turn.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    /**
     * 标记轮次开始进行追问决策。
     */
    @Override
    public void markFollowUpDeciding(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.FOLLOW_UP_DECIDING.name());
    }

    /**
     * 标记追问轮次已经创建。
     */
    @Override
    public void markFollowUpCreated(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.FOLLOW_UP_CREATED.name());
    }

    /**
     * 标记下一道主问题已经创建。
     */
    @Override
    public void markNextMainCreated(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.NEXT_MAIN_CREATED.name());
    }

    /**
     * 标记会话已经完成。
     */
    @Override
    public void markSessionCompleted(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.SESSION_COMPLETED.name());
    }

    /**
     * 生成答案内容的 SHA-256 摘要，用于构造轮次幂等键。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
