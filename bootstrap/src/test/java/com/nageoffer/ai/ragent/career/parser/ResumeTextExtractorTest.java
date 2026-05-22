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

package com.nageoffer.ai.ragent.career.parser;

import com.nageoffer.ai.ragent.career.service.parser.ResumeTextExtractor;
import com.nageoffer.ai.ragent.career.service.ocr.ResumeOcrRequest;
import com.nageoffer.ai.ragent.career.service.ocr.ResumeOcrResult;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsTextFromPlainTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "Java backend engineer".getBytes(StandardCharsets.UTF_8)
        );

        Assertions.assertEquals("Java backend engineer", extractor.extract(file));
    }

    @Test
    void rejectsEmptyResumeFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                new byte[0]
        );

        ClientException ex = Assertions.assertThrows(
                ClientException.class,
                () -> extractor.extract(file)
        );
        Assertions.assertEquals("Resume file is empty", ex.getMessage());
    }

    @Test
    void rejectsResumeWithoutExtractedText() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        ClientException ex = Assertions.assertThrows(
                ClientException.class,
                () -> extractor.extract(file)
        );
        Assertions.assertEquals("Resume text is empty", ex.getMessage());
    }

    @Test
    void fallsBackToOcrForImageResumeWhenTikaExtractsNoText() {
        AtomicReference<ResumeOcrRequest> capturedRequest = new AtomicReference<>();
        ResumeTextExtractor imageExtractor = new ResumeTextExtractor(request -> {
            capturedRequest.set(request);
            return new ResumeOcrResult("王小明 Java 后端工程师", List.of("王小明", "Java 后端工程师"),
                    "test-ocr", "trace-ocr");
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        Assertions.assertEquals("王小明 Java 后端工程师", imageExtractor.extract(file));
        Assertions.assertNotNull(capturedRequest.get());
        Assertions.assertEquals("png", capturedRequest.get().imageFormat());
        Assertions.assertEquals("resume.png", capturedRequest.get().sourceName());
        Assertions.assertArrayEquals(new byte[]{1, 2, 3, 4}, capturedRequest.get().imageBytes());
    }

    @Test
    void rendersPdfPagesForOcrWhenPdfHasNoEmbeddedText() throws IOException {
        AtomicReference<ResumeOcrRequest> capturedRequest = new AtomicReference<>();
        ResumeTextExtractor pdfExtractor = new ResumeTextExtractor(request -> {
            capturedRequest.set(request);
            return new ResumeOcrResult("扫描版 PDF 简历", List.of("扫描版 PDF 简历"), "test-ocr", "trace-ocr");
        });
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                blankPdf()
        );

        Assertions.assertEquals("扫描版 PDF 简历", pdfExtractor.extract(file));
        Assertions.assertNotNull(capturedRequest.get());
        Assertions.assertEquals("png", capturedRequest.get().imageFormat());
        Assertions.assertEquals(1, capturedRequest.get().pageNumber());
        Assertions.assertEquals("resume.pdf", capturedRequest.get().sourceName());
    }

    private byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
