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

    /**
     * 根据会话、轮次和答案内容构造答题步骤幂等键。
     */
    String buildStepIdempotencyKey(String sessionId, Integer turnNo, String answerRevision, String answer);

    /**
     * 判断当前轮次是否已经处理过同一个幂等步骤。
     */
    boolean isSameStep(InterviewTurnDO turn, String stepIdempotencyKey);

    /**
     * 初始化一个待作答的面试轮次。
     */
    void initializeAskedTurn(InterviewTurnDO turn);

    /**
     * 标记候选人答案已保存，并绑定本次答题幂等键。
     */
    void markAnswerSaved(InterviewTurnDO turn, String answer, String stepIdempotencyKey);

    /**
     * 标记轮次进入评分中，并累加评分尝试次数。
     */
    void markEvaluating(InterviewTurnDO turn);

    /**
     * 标记轮次评分完成。
     */
    void markEvaluated(InterviewTurnDO turn);

    /**
     * 标记补偿评分成功，保留评分完成态并记录补偿完成状态。
     */
    void markEvaluationCompensated(InterviewTurnDO turn);

    /**
     * 标记补偿评分任务已被抢占，防止多个补偿实例重复推进同一轮。
     */
    void markEvaluationRetryClaimed(InterviewTurnDO turn);

    /**
     * 标记评分失败并进入等待补偿重试状态。
     */
    void markEvaluationFailed(InterviewTurnDO turn, RuntimeException ex);

    /**
     * 标记轮次开始进行追问决策。
     */
    void markFollowUpDeciding(InterviewTurnDO turn);

    /**
     * 标记追问轮次已经创建。
     */
    void markFollowUpCreated(InterviewTurnDO turn);

    /**
     * 标记下一道主问题已经创建。
     */
    void markNextMainCreated(InterviewTurnDO turn);

    /**
     * 标记会话已经完成。
     */
    void markSessionCompleted(InterviewTurnDO turn);
}
