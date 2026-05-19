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
     * 校验原始 Markdown 不能绕过结构化姓名校验。
     */
    @Test
    void rawMarkdownDoesNotSatisfyStructuredNameValidation() {
        ResumeRenderValidationResult result = pipeline.validate(ResumeVersionDO.builder()
                .id("version-raw")
                .contentJson("{\"basic\":{}}")
                .markdownContent("# Alice")
                .build(), "MARKDOWN");

        assertFalse(result.valid());
        assertTrue(result.missingFields().contains("basic.name"));
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
                .contains("<!DOCTYPE html>", "<h1>Alice</h1>", "Java engineer")
                .doesNotContain("<p>Java engineer</p>");
        assertEquals("resume-version-1.html", output.fileName());
        assertEquals("text/html", output.contentType());
    }

    /**
     * 校验结构化 JSON 会先映射到模板字段，再渲染成 Markdown，而不是直接输出原始 JSON。
     */
    @Test
    void markdownRenderUsesTemplateFieldsFromContentJson() {
        ResumeVersionDO version = ResumeVersionDO.builder()
                .id("version-json")
                .title("张三简历")
                .contentJson("""
                        {
                          "basic": {
                            "name": "张三",
                            "headline": "后端开发工程师",
                            "phone": "13800000000",
                            "email": "zhangsan@example.com"
                          },
                          "summary": "负责高并发业务平台和智能简历交付。",
                          "skills": ["Java", "Spring Boot", "Redis"],
                          "projects": [
                            {
                              "name": "Ragent Career",
                              "role": "核心开发",
                              "description": "建设简历模板渲染和导出失效闭环。",
                              "techStack": ["Java 17", "PostgreSQL"]
                            }
                          ],
                          "experiences": [
                            {
                              "company": "示例科技",
                              "position": "后端工程师",
                              "startDate": "2022.01",
                              "endDate": "至今",
                              "responsibilities": ["负责导出链路", "维护任务追踪"]
                            }
                          ],
                          "education": [
                            {
                              "school": "示例大学",
                              "degree": "本科",
                              "major": "计算机科学",
                              "startDate": "2018",
                              "endDate": "2022"
                            }
                          ]
                        }
                        """)
                .build();

        ResumeRenderOutput output = pipeline.render(version, "MARKDOWN");

        String markdown = new String(output.content(), StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(markdown)
                .contains("# 张三", "后端开发工程师", "## 技能", "- Java", "Ragent Career", "示例大学")
                .doesNotContain("{\"basic\"");
        assertEquals("resume-version-json.md", output.fileName());
        assertEquals("text/markdown", output.contentType());
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
