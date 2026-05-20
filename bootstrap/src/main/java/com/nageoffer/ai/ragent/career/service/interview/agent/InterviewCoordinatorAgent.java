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
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InterviewCoordinatorAgent {

    private final CareerSingleFlightLlmService singleFlightLlmService;

    private final CareerJsonParser careerJsonParser;

    /**
     * 基于 JD 对齐摘要生成阶段化面试计划。
     */
    public Map<String, Object> plan(ResumeVersionDO resumeVersion,
                                    JobDescriptionDO job,
                                    Map<String, Object> jdAlignment,
                                    CareerRetrievalEnhancement enhancement) {
        String scene = CareerInterviewAgentType.COORDINATOR.scene();
        String prompt = String.format(CareerPromptTemplates.INTERVIEW_COORDINATOR_PLAN,
                defaultJson(resumeVersion.getContentJson()),
                defaultJson(job.getParsedJson()),
                String.valueOf(jdAlignment),
                enhancementPayload(enhancement));
        String response = singleFlightLlmService.chat(scene,
                buildKey(scene, resumeVersion.getUserId(), resumeVersion.getId() + ":" + job.getId()),
                null,
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
    private String buildKey(String scene, String userId, String artifactId) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "INTERVIEW_COORDINATOR"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"));
    }

    /**
     * 读取 JSON 文本，空值时返回空对象。
     */
    private String defaultJson(String value) {
        return StrUtil.blankToDefault(value, "{}");
    }

    /**
     * 将检索增强上下文转换为提示词片段。
     */
    private String enhancementPayload(CareerRetrievalEnhancement enhancement) {
        return enhancement == null ? "{}" : String.valueOf(enhancement.toPromptPayload());
    }
}
