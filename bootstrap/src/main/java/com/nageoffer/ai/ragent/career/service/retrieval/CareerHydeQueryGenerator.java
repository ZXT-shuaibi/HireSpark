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

package com.nageoffer.ai.ragent.career.service.retrieval;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CareerHydeQueryGenerator {

    private static final String SCENE = "CAREER_HYDE";
    private static final int HYDE_RESULT_MAX_LENGTH = 1200;

    private final CareerSingleFlightLlmService singleFlightLlmService;

    /**
     * 按检索场景生成仅用于查询的 HyDE 虚拟画像，调用方负责空结果降级。
     */
    public String generate(CareerRetrievalScenario scenario,
                           ResumeVersionDO resumeVersion,
                           JobDescriptionDO job,
                           String querySeed) {
        CareerRetrievalScenario actualScenario = scenario == null ? CareerRetrievalScenario.ALIGNMENT : scenario;
        String prompt = buildPrompt(actualScenario, resumeVersion, job, querySeed);
        String response = singleFlightLlmService.chat(SCENE,
                buildSingleFlightKey(actualScenario, resumeVersion, job, querySeed),
                buildTraceId(actualScenario, resumeVersion, job, querySeed),
                ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(prompt)))
                        .temperature(0.2D)
                        .maxTokens(1000)
                        .thinking(false)
                        .build());
        return limitText(response, HYDE_RESULT_MAX_LENGTH);
    }

    /**
     * 根据场景选择不同提示词，避免三个链路共用同一类检索意图。
     */
    private String buildPrompt(CareerRetrievalScenario scenario,
                               ResumeVersionDO resumeVersion,
                               JobDescriptionDO job,
                               String querySeed) {
        return switch (scenario) {
            case ALIGNMENT -> String.format(CareerPromptTemplates.HYDE_ALIGNMENT,
                    defaultJson(resumeVersion == null ? null : resumeVersion.getContentJson()),
                    defaultJson(job == null ? null : job.getParsedJson()),
                    defaultText(querySeed));
            case OPTIMIZATION -> String.format(CareerPromptTemplates.HYDE_OPTIMIZATION,
                    defaultJson(resumeVersion == null ? null : resumeVersion.getContentJson()),
                    defaultJson(job == null ? null : job.getParsedJson()),
                    defaultText(querySeed));
            case INTERVIEW -> String.format(CareerPromptTemplates.HYDE_INTERVIEW,
                    defaultJson(resumeVersion == null ? null : resumeVersion.getContentJson()),
                    defaultJson(job == null ? null : job.getParsedJson()),
                    defaultText(querySeed));
        };
    }

    /**
     * 构建稳定且短的 single-flight 原始键，具体存储键仍由统一服务二次摘要。
     */
    private String buildSingleFlightKey(CareerRetrievalScenario scenario,
                                        ResumeVersionDO resumeVersion,
                                        JobDescriptionDO job,
                                        String querySeed) {
        return String.join(":",
                SCENE,
                scenario.name(),
                StrUtil.blankToDefault(resumeVersion == null ? null : resumeVersion.getId(), "resume"),
                StrUtil.blankToDefault(job == null ? null : job.getId(), "job"),
                sha256(defaultText(querySeed)));
    }

    /**
     * 构建便于排查的 traceId，不携带完整 seed，避免日志字段过长。
     */
    private String buildTraceId(CareerRetrievalScenario scenario,
                                ResumeVersionDO resumeVersion,
                                JobDescriptionDO job,
                                String querySeed) {
        return String.join("-",
                "career-hyde",
                scenario.name().toLowerCase(Locale.ROOT),
                StrUtil.blankToDefault(resumeVersion == null ? null : resumeVersion.getId(), "resume"),
                StrUtil.blankToDefault(job == null ? null : job.getId(), "job"),
                sha256(defaultText(querySeed)).substring(0, 12));
    }

    /**
     * 计算文本 SHA-256 摘要。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException("Failed to build Career HyDE single-flight key");
        }
    }

    private String defaultJson(String text) {
        return StrUtil.blankToDefault(trimToNull(text), "{}");
    }

    private String defaultText(String text) {
        return StrUtil.blankToDefault(trimToNull(text), "");
    }

    private String limitText(String text, int maxLength) {
        String value = StrUtil.blankToDefault(text, "").trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
