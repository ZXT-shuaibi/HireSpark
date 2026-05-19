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
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderOutput;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderPipeline;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeRenderPipelineTest {

    private final ResumeRenderPipeline pipeline = new ResumeRenderPipeline();

    /**
     * 校验缺少基础字段时渲染前置检查会失败。
     */
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

    /**
     * 校验 PDF 和 Word 导出格式已经开启渲染能力。
     */
    @Test
    void pdfAndWordAreSupportedByValidation() {
        ResumeVersionDO version = ResumeVersionDO.builder()
                .id("version-1")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .markdownContent("# Alice")
                .build();

        ResumeRenderValidationResult pdf = pipeline.validate(version, "PDF");
        ResumeRenderValidationResult word = pipeline.validate(version, "WORD");

        assertTrue(pdf.valid());
        assertTrue(pdf.rendererEnabled());
        assertNull(pdf.disabledReason());
        assertNotEquals("PDF renderer is not enabled", pdf.disabledReason());
        assertTrue(word.valid());
        assertTrue(word.rendererEnabled());
        assertNull(word.disabledReason());
        assertNotEquals("WORD renderer is not enabled", word.disabledReason());
    }

    /**
     * 校验 HTML 渲染会把 Markdown 标题和段落转换成页面元素。
     */
    @Test
    void htmlRenderContainsConvertedHeadingAndParagraph() {
        ResumeRenderOutput output = pipeline.render(resumeVersionWithMarkdown(), "HTML");

        String html = new String(output.content(), StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("<!DOCTYPE html>", "<h1>Alice</h1>", "<p>Java engineer</p>");
        assertEquals("resume-version-1.html", output.fileName());
        assertEquals("text/html", output.contentType());
    }

    /**
     * 校验 PDF 渲染会生成合法 PDF 头部。
     */
    @Test
    void pdfRenderStartsWithPdfHeader() {
        ResumeRenderOutput output = pipeline.render(resumeVersionWithMarkdown(), "PDF");

        assertTrue(new String(output.content(), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertEquals("resume-version-1.pdf", output.fileName());
        assertEquals("application/pdf", output.contentType());
    }

    /**
     * 校验 Word 渲染会生成包含主文档的 DOCX 压缩包。
     */
    @Test
    void wordRenderCreatesDocxZipWithDocumentXml() throws Exception {
        ResumeRenderOutput output = pipeline.render(resumeVersionWithMarkdown(), "WORD");

        assertTrue(containsZipEntry(output.content(), "word/document.xml"));
        assertEquals("resume-version-1.docx", output.fileName());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", output.contentType());
    }

    /**
     * 构造带 Markdown 正文的简历版本。
     */
    private ResumeVersionDO resumeVersionWithMarkdown() {
        return ResumeVersionDO.builder()
                .id("version-1")
                .title("Alice Resume")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .markdownContent("# Alice\n\nJava engineer")
                .build();
    }

    /**
     * 判断 DOCX 压缩包内是否包含指定条目。
     */
    private boolean containsZipEntry(byte[] content, String expectedName) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(new java.io.ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (expectedName.equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        }
    }
}
