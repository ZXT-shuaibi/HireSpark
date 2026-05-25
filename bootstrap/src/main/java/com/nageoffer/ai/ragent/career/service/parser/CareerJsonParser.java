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

package com.nageoffer.ai.ragent.career.service.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CareerJsonParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> parseObject(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("AI response is blank");
        }
        try {
            return objectMapper.readValue(extractJsonObject(stripFence(response.trim())), MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI response is not valid JSON", ex);
        }
    }

    private String stripFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        String withoutStart = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return text;
        }
        return text.substring(start, end + 1);
    }
}
