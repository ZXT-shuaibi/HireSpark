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

package com.nageoffer.ai.ragent.career.schedule;

import com.nageoffer.ai.ragent.career.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class InterviewEvaluationCompensationWorker {

    private final InterviewSessionService interviewSessionService;

    @Value("${career.interview.compensation.enabled:true}")
    private boolean enabled;

    @Value("${career.interview.compensation.batch-size:20}")
    private int batchSize;

    /**
     * 定时补偿评分失败的面试轮次，避免候选人会话长期卡在待重试状态。
     */
    @Scheduled(fixedDelayString = "${career.interview.compensation.delay-ms:30000}",
            initialDelayString = "${career.interview.compensation.initial-delay-ms:30000}")
    public void compensateFailedEvaluations() {
        if (!enabled) {
            log.debug("面试评分补偿已关闭，跳过本轮调度");
            return;
        }
        try {
            int compensated = interviewSessionService.compensatePendingEvaluations(batchSize);
            log.info("本轮面试评分补偿完成，处理数量={}", compensated);
        } catch (RuntimeException ex) {
            log.warn("本轮面试评分补偿失败", ex);
        }
    }
}
