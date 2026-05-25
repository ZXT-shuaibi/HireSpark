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

import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewAnswerRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewCreateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;

public interface InterviewSessionService {

    CareerInterviewSessionVO createSession(CareerInterviewCreateRequest request);

    CareerInterviewSessionVO querySession(String sessionId);

    CareerInterviewTurnVO nextQuestion(String sessionId);

    CareerInterviewTurnVO submitAnswer(String sessionId, CareerInterviewAnswerRequest request);

    /**
     * 重试指定轮次的面试评分，复用已保存的答案继续推进面试流程。
     */
    CareerInterviewTurnVO retryEvaluation(String sessionId, Integer turnNo);

    /**
     * 批量补偿待重试的面试评分轮次，供后台 worker 调度使用。
     */
    int compensatePendingEvaluations(int limit);

    void pause(String sessionId);

    void finish(String sessionId);
}
