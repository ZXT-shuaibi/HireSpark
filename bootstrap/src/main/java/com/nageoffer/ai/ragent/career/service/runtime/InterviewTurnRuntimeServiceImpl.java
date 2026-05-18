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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class InterviewTurnRuntimeServiceImpl implements InterviewTurnRuntimeService {

    @Override
    public String buildStepIdempotencyKey(String sessionId, Integer turnNo, String answerRevision, String answer) {
        String revision = StrUtil.isBlank(answerRevision) ? sha256(answer) : answerRevision.trim();
        return sessionId + ":" + turnNo + ":" + revision;
    }

    @Override
    public boolean isSameStep(InterviewTurnDO turn, String stepIdempotencyKey) {
        return turn != null && StrUtil.isNotBlank(stepIdempotencyKey)
                && stepIdempotencyKey.equals(turn.getStepIdempotencyKey());
    }

    @Override
    public void initializeAskedTurn(InterviewTurnDO turn) {
        turn.setStatus("ASKED");
        turn.setAnswerStatus(InterviewRuntimeStatus.WAITING_ANSWER.name());
        turn.setEvaluationStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.NONE.name());
        turn.setAttemptCount(0);
        turn.setLastError(null);
    }

    @Override
    public void markAnswerSaved(InterviewTurnDO turn, String answer, String stepIdempotencyKey) {
        turn.setAnswer(answer);
        turn.setStepIdempotencyKey(stepIdempotencyKey);
        turn.setStatus("ANSWERED");
        turn.setAnswerStatus(InterviewRuntimeStatus.ANSWER_SAVED.name());
        turn.setEvaluationStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.NOT_STARTED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.NONE.name());
        turn.setLastError(null);
    }

    @Override
    public void markEvaluating(InterviewTurnDO turn) {
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATING.name());
        turn.setAttemptCount((turn.getAttemptCount() == null ? 0 : turn.getAttemptCount()) + 1);
        turn.setLastError(null);
    }

    @Override
    public void markEvaluated(InterviewTurnDO turn) {
        turn.setStatus("EVALUATED");
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.NONE.name());
        turn.setLastError(null);
    }

    @Override
    public void markEvaluationFailed(InterviewTurnDO turn, RuntimeException ex) {
        turn.setStatus("WAITING_RETRY");
        turn.setEvaluationStatus(InterviewRuntimeStatus.EVALUATION_FAILED.name());
        turn.setCompensationStatus(InterviewRuntimeStatus.COMPENSATING.name());
        turn.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    @Override
    public void markFollowUpDeciding(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.FOLLOW_UP_DECIDING.name());
    }

    @Override
    public void markFollowUpCreated(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.FOLLOW_UP_CREATED.name());
    }

    @Override
    public void markNextMainCreated(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.NEXT_MAIN_CREATED.name());
    }

    @Override
    public void markSessionCompleted(InterviewTurnDO turn) {
        turn.setFollowUpDecisionStatus(InterviewRuntimeStatus.SESSION_COMPLETED.name());
    }

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
