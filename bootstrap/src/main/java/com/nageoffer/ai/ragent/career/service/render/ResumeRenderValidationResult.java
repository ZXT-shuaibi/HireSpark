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

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record ResumeRenderValidationResult(boolean valid,
                                           List<String> missingFields,
                                           String templateVersion,
                                           String contentType,
                                           List<String> warnings,
                                           String traceId,
                                           boolean rendererEnabled,
                                           String disabledReason) {

    public Map<String, Object> toPayload() {
        return Map.of(
                "valid", valid,
                "missingFields", missingFields == null ? List.of() : missingFields,
                "templateVersion", templateVersion == null ? "" : templateVersion,
                "contentType", contentType == null ? "" : contentType,
                "warnings", warnings == null ? List.of() : warnings,
                "traceId", traceId == null ? "" : traceId,
                "rendererEnabled", rendererEnabled,
                "disabledReason", disabledReason == null ? "" : disabledReason
        );
    }
}
