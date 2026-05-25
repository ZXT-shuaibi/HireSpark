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

package com.nageoffer.ai.ragent.career.service.progress;

import com.nageoffer.ai.ragent.career.dao.entity.CareerProgressEventDO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CareerProgressStreamService {

    /**
     * 订阅简历优化任务的实时进度流。
     */
    SseEmitter subscribeOptimization(String taskId, String userId);

    /**
     * 订阅面试会话的实时进度流。
     */
    SseEmitter subscribeInterview(String sessionId, String userId);

    /**
     * 发布已落库的简历优化进度事件。
     */
    void publishOptimization(CareerProgressEventDO event);

    /**
     * 发布已落库的面试进度事件。
     */
    void publishInterview(String sessionId, String userId, String eventType, Object payload);
}
