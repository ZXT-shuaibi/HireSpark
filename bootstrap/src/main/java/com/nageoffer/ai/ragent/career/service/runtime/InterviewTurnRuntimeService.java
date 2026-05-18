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

import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;

public interface InterviewTurnRuntimeService {

    String buildStepIdempotencyKey(String sessionId, Integer turnNo, String answerRevision, String answer);

    boolean isSameStep(InterviewTurnDO turn, String stepIdempotencyKey);

    void initializeAskedTurn(InterviewTurnDO turn);

    void markAnswerSaved(InterviewTurnDO turn, String answer, String stepIdempotencyKey);

    void markEvaluating(InterviewTurnDO turn);

    void markEvaluated(InterviewTurnDO turn);

    /**
     * 标记补偿评分成功，保留评分完成态并记录补偿完成状态。
     */
    void markEvaluationCompensated(InterviewTurnDO turn);

    /**
     * 标记补偿评分任务已被抢占，防止多个补偿实例重复推进同一轮。
     */
    void markEvaluationRetryClaimed(InterviewTurnDO turn);

    void markEvaluationFailed(InterviewTurnDO turn, RuntimeException ex);

    void markFollowUpDeciding(InterviewTurnDO turn);

    void markFollowUpCreated(InterviewTurnDO turn);

    void markNextMainCreated(InterviewTurnDO turn);

    void markSessionCompleted(InterviewTurnDO turn);
}
