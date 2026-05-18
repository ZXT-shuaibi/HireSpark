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

package com.nageoffer.ai.ragent.career.service.render;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ResumeRenderPipeline {

    public static final String TEMPLATE_VERSION = "career-resume-template-v1";

    private static final String EXPORT_TYPE_MARKDOWN = "MARKDOWN";
    private static final String EXPORT_TYPE_HTML = "HTML";
    private static final String EXPORT_TYPE_PDF = "PDF";
    private static final String EXPORT_TYPE_WORD = "WORD";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResumeRenderValidationResult validate(ResumeVersionDO version, String exportType) {
        String type = StrUtil.blankToDefault(exportType, "").trim().toUpperCase();
        String traceId = "career-render-" + (version == null ? "unknown" : version.getId()) + "-" + type.toLowerCase();
        List<String> missingFields = missingFields(version);
        List<String> warnings = new ArrayList<>();
        if (EXPORT_TYPE_PDF.equals(type) || EXPORT_TYPE_WORD.equals(type)) {
            return ResumeRenderValidationResult.builder()
                    .valid(missingFields.isEmpty())
                    .missingFields(missingFields)
                    .templateVersion(TEMPLATE_VERSION)
                    .warnings(warnings)
                    .traceId(traceId)
                    .rendererEnabled(false)
                    .disabledReason(type + " renderer is not enabled")
                    .build();
        }
        boolean supported = EXPORT_TYPE_MARKDOWN.equals(type) || EXPORT_TYPE_HTML.equals(type);
        if (!supported) {
            warnings.add("Unsupported export type: " + type);
        }
        return ResumeRenderValidationResult.builder()
                .valid(supported && missingFields.isEmpty())
                .missingFields(missingFields)
                .templateVersion(TEMPLATE_VERSION)
                .warnings(warnings)
                .traceId(traceId)
                .rendererEnabled(supported)
                .disabledReason(supported ? null : "Export type is not supported: " + type)
                .build();
    }

    private List<String> missingFields(ResumeVersionDO version) {
        List<String> missing = new ArrayList<>();
        if (version == null) {
            missing.add("resumeVersion");
            return missing;
        }
        if (StrUtil.isBlank(version.getContentJson()) && StrUtil.isBlank(version.getMarkdownContent())) {
            missing.add("content");
            return missing;
        }
        if (StrUtil.isBlank(version.getContentJson())) {
            return missing;
        }
        try {
            JsonNode root = objectMapper.readTree(version.getContentJson());
            JsonNode basic = root.path("basic");
            if (basic.isMissingNode() || StrUtil.isBlank(basic.path("name").asText(null))) {
                missing.add("basic.name");
            }
        } catch (Exception ex) {
            missing.add("contentJson");
        }
        return missing;
    }
}
