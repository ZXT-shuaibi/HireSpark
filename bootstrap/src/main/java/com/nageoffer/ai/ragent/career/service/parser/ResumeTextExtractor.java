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

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.service.ocr.ResumeOcrProvider;
import com.nageoffer.ai.ragent.career.service.ocr.ResumeOcrRequest;
import com.nageoffer.ai.ragent.career.service.ocr.ResumeOcrResult;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.Tika;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ResumeTextExtractor {

    private final Tika tika;
    private final ResumeOcrProvider ocrProvider;

    @Value("${career.resume.ocr.max-pdf-pages:3}")
    private int maxPdfOcrPages = 3;

    public ResumeTextExtractor() {
        this(new Tika(), null);
    }

    public ResumeTextExtractor(ResumeOcrProvider ocrProvider) {
        this(new Tika(), ocrProvider);
    }

    @Autowired
    public ResumeTextExtractor(ObjectProvider<ResumeOcrProvider> ocrProvider) {
        this(new Tika(), ocrProvider == null ? null : ocrProvider.getIfAvailable());
    }

    public ResumeTextExtractor(Tika tika, ResumeOcrProvider ocrProvider) {
        this.tika = tika == null ? new Tika() : tika;
        this.ocrProvider = ocrProvider;
    }

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("Resume file is empty");
        }
        try {
            byte[] content = file.getBytes();
            String text = extractByTika(content);
            if (text == null || text.trim().isEmpty()) {
                text = extractByOcr(file, content);
            }
            if (StrUtil.isBlank(text)) {
                throw new ClientException("Resume text is empty");
            }
            return text.trim();
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to read resume file", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String extractByTika(byte[] content) {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            return tika.parseToString(inputStream);
        } catch (Exception | LinkageError ex) {
            return null;
        }
    }

    private String extractByOcr(MultipartFile file, byte[] content) {
        if (ocrProvider == null) {
            return null;
        }
        List<OcrPageImage> images = buildOcrImages(file, content);
        if (images.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (OcrPageImage image : images) {
            ResumeOcrResult result = ocrProvider.recognize(new ResumeOcrRequest(
                    image.bytes(),
                    image.imageFormat(),
                    traceId(file, image.pageNumber()),
                    file.getOriginalFilename(),
                    image.pageNumber()));
            if (result != null && StrUtil.isNotBlank(result.text())) {
                parts.add(result.text().trim());
            }
        }
        return String.join("\n", parts).trim();
    }

    private List<OcrPageImage> buildOcrImages(MultipartFile file, byte[] content) {
        if (isImageFile(file)) {
            return List.of(new OcrPageImage(content, imageFormat(file), 1));
        }
        if (isPdfFile(file)) {
            return renderPdfPagesForOcr(content);
        }
        return List.of();
    }

    private List<OcrPageImage> renderPdfPagesForOcr(byte[] content) {
        List<OcrPageImage> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), effectiveMaxPdfOcrPages());
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ImageIO.write(image, "png", outputStream);
                images.add(new OcrPageImage(outputStream.toByteArray(), "png", i + 1));
            }
            return images;
        } catch (Exception ex) {
            throw new ServiceException("Failed to render PDF pages for OCR", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private int effectiveMaxPdfOcrPages() {
        return Math.max(1, Math.min(maxPdfOcrPages, 10));
    }

    private boolean isImageFile(MultipartFile file) {
        String contentType = StrUtil.blankToDefault(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/")) {
            return true;
        }
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".webp") || name.endsWith(".bmp")
                || name.endsWith(".tif") || name.endsWith(".tiff");
    }

    private boolean isPdfFile(MultipartFile file) {
        String contentType = StrUtil.blankToDefault(file.getContentType(), "").toLowerCase(Locale.ROOT);
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        return contentType.equals("application/pdf") || name.endsWith(".pdf");
    }

    private String imageFormat(MultipartFile file) {
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1);
            return "jpeg".equals(extension) ? "jpg" : extension;
        }
        String contentType = StrUtil.blankToDefault(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/")) {
            String type = contentType.substring("image/".length());
            return "jpeg".equals(type) ? "jpg" : type;
        }
        return "png";
    }

    private String traceId(MultipartFile file, int pageNumber) {
        String token = StrUtil.blankToDefault(file.getOriginalFilename(), "resume");
        return "resume-ocr-" + Integer.toUnsignedString(token.hashCode()) + "-p" + pageNumber;
    }

    private record OcrPageImage(byte[] bytes, String imageFormat, int pageNumber) {
    }
}
