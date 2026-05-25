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

package com.nageoffer.ai.ragent.career.service.interview.agent;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InterviewReflectorAgent {

    private final CareerSingleFlightLlmService singleFlightLlmService;

    private final CareerJsonParser careerJsonParser;

    /**
     * 反思当前轮次评分结果，并输出流程裁决建议。
     */
    public Map<String, Object> reflect(InterviewSessionDO session,
                                       InterviewTurnDO turn,
                                       Map<String, Object> evaluationPayload,
                                       List<InterviewTurnDO> sessionTurns) {
        String scene = CareerInterviewAgentType.REFLECTOR.scene();
        String prompt = String.format(CareerPromptTemplates.INTERVIEW_REFLECTOR,
                String.valueOf(session.getPlanJson()),
                turn.getQuestion(),
                turn.getAnswer(),
                String.valueOf(evaluationPayload),
                String.valueOf(sessionTurns));
        String response = singleFlightLlmService.chat(scene,
                buildKey(scene, session.getUserId(), session.getId(), turn.getTurnNo()),
                session.getTraceId(),
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(prompt)))
                        .temperature(0.1D)
                        .thinking(false)
                        .build());
        return careerJsonParser.parseObject(response);
    }

    /**
     * 构建 Agent 调用的幂等键。
     */
    private String buildKey(String scene, String userId, String sessionId, Integer turnNo) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "INTERVIEW_REFLECTOR"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(sessionId, "session"),
                String.valueOf(turnNo == null ? 0 : turnNo));
    }
}
