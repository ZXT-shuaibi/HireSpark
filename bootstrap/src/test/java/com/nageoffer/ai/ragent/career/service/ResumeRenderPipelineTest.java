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

import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderPipeline;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeRenderPipelineTest {

    private final ResumeRenderPipeline pipeline = new ResumeRenderPipeline();

    @Test
    void missingRequiredFieldsFailValidation() {
        ResumeRenderValidationResult result = pipeline.validate(ResumeVersionDO.builder()
                .id("version-1")
                .contentJson("{\"basic\":{}}")
                .build(), "HTML");

        assertFalse(result.valid());
        assertTrue(result.rendererEnabled());
        assertEquals(ResumeRenderPipeline.TEMPLATE_VERSION, result.templateVersion());
        assertTrue(result.missingFields().contains("basic.name"));
        assertTrue(result.traceId().contains("career-render-version-1-html"));
    }

    @Test
    void disabledPdfAndWordReturnClearDisabledResult() {
        ResumeVersionDO version = ResumeVersionDO.builder()
                .id("version-1")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .build();

        ResumeRenderValidationResult pdf = pipeline.validate(version, "PDF");
        ResumeRenderValidationResult word = pipeline.validate(version, "WORD");

        assertTrue(pdf.valid());
        assertFalse(pdf.rendererEnabled());
        assertEquals("PDF renderer is not enabled", pdf.disabledReason());
        assertTrue(word.valid());
        assertFalse(word.rendererEnabled());
        assertEquals("WORD renderer is not enabled", word.disabledReason());
    }
}
