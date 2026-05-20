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
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TechnicalInterviewerAgent {

    private final CareerSingleFlightLlmService singleFlightLlmService;

    private final CareerJsonParser careerJsonParser;

    /**
     * 由技术面试官 Agent 生成或选择下一道问题，失败时回退到协调者计划题。
     */
    public Map<String, Object> selectQuestion(String userId,
                                              String sessionOrArtifactId,
                                              String traceId,
                                              Map<String, Object> plan,
                                              Map<String, Object> plannedQuestion,
                                              int questionIndex,
                                              List<?> previousTurns) {
        String scene = CareerInterviewAgentType.TECHNICAL_INTERVIEWER.scene();
        String prompt = String.format(CareerPromptTemplates.INTERVIEW_TECHNICAL_QUESTION,
                String.valueOf(plan),
                String.valueOf(plannedQuestion),
                questionIndex,
                String.valueOf(previousTurns));
        String response = singleFlightLlmService.chat(scene,
                buildKey(scene, userId, sessionOrArtifactId, questionIndex),
                traceId,
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(prompt)))
                        .temperature(0.1D)
                        .thinking(false)
                        .build());
        Map<String, Object> parsed = careerJsonParser.parseObject(response);
        if (StrUtil.isNotBlank(text(parsed.get("question")))) {
            return parsed;
        }
        return plannedQuestion == null ? new LinkedHashMap<>() : new LinkedHashMap<>(plannedQuestion);
    }

    /**
     * 构建 Agent 调用的幂等键。
     */
    private String buildKey(String scene, String userId, String artifactId, int questionIndex) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "INTERVIEW_TECHNICAL"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"),
                String.valueOf(questionIndex));
    }

    /**
     * 提取短文本。
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
