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

package com.nageoffer.ai.ragent.career.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.controller.request.CareerResumeExportRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerResumeUpdateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeExportVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeUploadVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeVersionVO;
import com.nageoffer.ai.ragent.career.dao.entity.CandidateProfileDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeDocumentDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeExportRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CandidateProfileMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeDocumentMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeExportRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.service.CandidateProfileService;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.parser.ResumeTextExtractor;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderPipeline;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderValidationResult;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import com.nageoffer.ai.ragent.rag.util.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateProfileServiceImpl implements CandidateProfileService {

    private static final String PARSE_STATUS_RUNNING = "RUNNING";
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSE_STATUS_FAILED = "FAILED";
    private static final String SOURCE_TYPE_PARSED = "PARSED";
    private static final String EXPORT_STATUS_RUNNING = "RUNNING";
    private static final String EXPORT_STATUS_SUCCESS = "SUCCESS";
    private static final String EXPORT_STATUS_FAILED = "FAILED";
    private static final String EXPORT_TYPE_MARKDOWN = "MARKDOWN";
    private static final String EXPORT_TYPE_HTML = "HTML";
    private static final String EXPORT_TYPE_PDF = "PDF";
    private static final String EXPORT_TYPE_WORD = "WORD";

    private final CandidateProfileMapper candidateProfileMapper;
    private final ResumeDocumentMapper resumeDocumentMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeExportRecordMapper resumeExportRecordMapper;
    private final ResumeTextExtractor resumeTextExtractor;
    private final CareerJsonParser careerJsonParser;
    private final CareerSingleFlightLlmService singleFlightLlmService;
    private final FileStorageService fileStorageService;
    private final ResumeRenderPipeline resumeRenderPipeline;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${career.storage.export-bucket:career-resume-export}")
    private String exportBucketName = "career-resume-export";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CareerResumeUploadVO uploadAndParse(MultipartFile file) {
        String userId = requireUserId();
        String rawText = resumeTextExtractor.extract(file);
        ResumeDocumentDO document = ResumeDocumentDO.builder()
                .userId(userId)
                .originalName(file.getOriginalFilename())
                .fileType(resolveFileType(file))
                .fileSize(file.getSize())
                .parseStatus(PARSE_STATUS_RUNNING)
                .rawText(rawText)
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        resumeDocumentMapper.insert(document);

        try {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(String.format(CareerPromptTemplates.RESUME_PARSE, rawText))))
                    .temperature(0.1D)
                    .thinking(false)
                    .build();
            String response = singleFlightLlmService.chat("RESUME_PARSE",
                    buildSingleFlightKey("RESUME_PARSE", userId, document.getId(), rawText),
                    "career-resume-parse-" + document.getId(),
                    chatRequest);
            Map<String, Object> resumeJson = careerJsonParser.parseObject(response);
            String contentJson = writeJson(resumeJson);

            CandidateProfileDO profile = upsertProfile(userId, resumeJson, contentJson);
            ResumeVersionDO version = ResumeVersionDO.builder()
                    .userId(userId)
                    .profileId(profile.getId())
                    .documentId(document.getId())
                    .title(defaultTitle(profile))
                    .versionNo(nextVersionNo(userId, profile.getId()))
                    .sourceType(SOURCE_TYPE_PARSED)
                    .contentJson(contentJson)
                    .markdownContent(rawText)
                    .active(1)
                    .createdBy(userId)
                    .updatedBy(userId)
                    .build();
            resumeVersionMapper.insert(version);

            document.setProfileId(profile.getId());
            document.setParseStatus(PARSE_STATUS_SUCCESS);
            document.setParseError(null);
            document.setUpdatedBy(userId);
            resumeDocumentMapper.updateById(document);

            return CareerResumeUploadVO.builder()
                    .documentId(document.getId())
                    .profileId(profile.getId())
                    .resumeVersionId(version.getId())
                    .parseStatus(PARSE_STATUS_SUCCESS)
                    .build();
        } catch (RuntimeException ex) {
            document.setParseStatus(PARSE_STATUS_FAILED);
            document.setParseError(trimError(ex));
            document.setUpdatedBy(userId);
            resumeDocumentMapper.updateById(document);
            throw ex;
        }
    }

    @Override
    public CareerResumeVersionVO queryVersion(String versionId) {
        return toVersionVO(requireVersion(versionId));
    }

    @Override
    public List<CareerResumeVersionVO> listVersions(String profileId) {
        String userId = requireUserId();
        if (StrUtil.isBlank(profileId)) {
            throw new ClientException("Profile id is required");
        }
        CandidateProfileDO profile = candidateProfileMapper.selectOne(
                Wrappers.lambdaQuery(CandidateProfileDO.class)
                        .eq(CandidateProfileDO::getId, profileId)
                        .eq(CandidateProfileDO::getUserId, userId)
                        .eq(CandidateProfileDO::getDeleted, 0)
        );
        if (profile == null) {
            throw new ClientException("Profile does not exist");
        }
        return resumeVersionMapper.selectList(
                        Wrappers.lambdaQuery(ResumeVersionDO.class)
                                .eq(ResumeVersionDO::getProfileId, profileId)
                                .eq(ResumeVersionDO::getUserId, userId)
                                .eq(ResumeVersionDO::getDeleted, 0)
                                .orderByDesc(ResumeVersionDO::getVersionNo)
                                .orderByDesc(ResumeVersionDO::getCreateTime)
                ).stream()
                .map(this::toVersionVO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerResumeVersionVO updateVersion(String versionId, CareerResumeUpdateRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Resume update request is required");
        }
        ResumeVersionDO version = requireVersion(versionId);
        if (StrUtil.isNotBlank(request.getTitle())) {
            version.setTitle(request.getTitle().trim());
        }
        if (request.getContentJson() != null) {
            version.setContentJson(normalizeContentJson(request.getContentJson()));
        }
        if (request.getMarkdownContent() != null) {
            version.setMarkdownContent(request.getMarkdownContent());
        }
        version.setUpdatedBy(userId);
        resumeVersionMapper.updateById(version);
        return toVersionVO(version);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteVersion(String versionId) {
        ResumeVersionDO version = requireVersion(versionId);
        version.setActive(0);
        version.setUpdatedBy(requireUserId());
        resumeVersionMapper.updateById(version);
        List<String> exportFileUrls = invalidateExportRecords(version.getId(), version.getUserId());
        resumeVersionMapper.deleteById(version.getId());
        deleteExportFilesAfterDbWork(exportFileUrls);
    }

    @Override
    public CareerResumeExportVO export(CareerResumeExportRequest request) {
        String userId = requireUserId();
        if (request == null) {
            throw new ClientException("Resume export request is required");
        }
        String exportType = normalizeExportType(request.getExportType());
        ResumeVersionDO version = requireVersion(request.getResumeVersionId());
        ResumeRenderValidationResult validation = resumeRenderPipeline.validate(version, exportType);
        ResumeExportRecordDO record = ResumeExportRecordDO.builder()
                .userId(userId)
                .resumeVersionId(version.getId())
                .exportType(exportType)
                .status(EXPORT_STATUS_RUNNING)
                .templateVersion(validation.templateVersion())
                .validationResultJson(writeJson(validation.toPayload()))
                .traceId(validation.traceId())
                .build();
        resumeExportRecordMapper.insert(record);

        try {
            if (!validation.valid() || !validation.rendererEnabled()) {
                throw new ClientException(firstNotBlank(validation.disabledReason(), "Resume render validation failed"));
            }
            ExportPayload payload = buildExportPayload(version, exportType);
            StoredFileDTO stored = fileStorageService.upload(
                    exportBucketName,
                    payload.content(),
                    payload.fileName(),
                    payload.contentType()
            );
            record.setFileUrl(stored.getUrl());
            record.setStatus(EXPORT_STATUS_SUCCESS);
            record.setErrorMessage(null);
            try {
                resumeExportRecordMapper.updateById(record);
            } catch (RuntimeException ex) {
                if (cleanupUploadedExport(stored.getUrl(), ex)) {
                    record.setFileUrl(null);
                }
                throw ex;
            }
            return CareerResumeExportVO.builder()
                    .id(record.getId())
                    .resumeVersionId(record.getResumeVersionId())
                    .exportType(record.getExportType())
                    .fileUrl(record.getFileUrl())
                    .status(record.getStatus())
                    .errorMessage(record.getErrorMessage())
                    .templateVersion(record.getTemplateVersion())
                    .validationResultJson(record.getValidationResultJson())
                    .traceId(record.getTraceId())
                    .build();
        } catch (RuntimeException ex) {
            record.setStatus(EXPORT_STATUS_FAILED);
            record.setErrorMessage(trimError(ex));
            try {
                resumeExportRecordMapper.updateById(record);
            } catch (RuntimeException updateEx) {
                ex.addSuppressed(updateEx);
            }
            if (ex instanceof ClientException
                    && (EXPORT_TYPE_PDF.equals(exportType) || EXPORT_TYPE_WORD.equals(exportType)
                    || !validation.valid() || !validation.rendererEnabled())) {
                return CareerResumeExportVO.builder()
                        .id(record.getId())
                        .resumeVersionId(record.getResumeVersionId())
                        .exportType(record.getExportType())
                        .fileUrl(record.getFileUrl())
                        .status(record.getStatus())
                        .errorMessage(record.getErrorMessage())
                        .templateVersion(record.getTemplateVersion())
                        .validationResultJson(record.getValidationResultJson())
                        .traceId(record.getTraceId())
                        .build();
            }
            throw ex;
        }
    }

    private CandidateProfileDO upsertProfile(String userId, Map<String, Object> resumeJson, String contentJson) {
        CandidateProfileDO profile = candidateProfileMapper.selectOne(
                Wrappers.lambdaQuery(CandidateProfileDO.class)
                        .eq(CandidateProfileDO::getUserId, userId)
                        .eq(CandidateProfileDO::getDeleted, 0)
                        .last("limit 1")
        );
        if (profile == null) {
            profile = CandidateProfileDO.builder()
                    .userId(userId)
                    .displayName(extractBasicValue(resumeJson, "name"))
                    .headline(extractBasicValue(resumeJson, "headline"))
                    .summary(extractString(resumeJson.get("summary")))
                    .profileJson(contentJson)
                    .createdBy(userId)
                    .updatedBy(userId)
                    .build();
            candidateProfileMapper.insert(profile);
            return profile;
        }

        profile.setDisplayName(firstNotBlank(extractBasicValue(resumeJson, "name"), profile.getDisplayName()));
        profile.setHeadline(firstNotBlank(extractBasicValue(resumeJson, "headline"), profile.getHeadline()));
        profile.setSummary(firstNotBlank(extractString(resumeJson.get("summary")), profile.getSummary()));
        profile.setProfileJson(contentJson);
        profile.setUpdatedBy(userId);
        candidateProfileMapper.updateById(profile);
        return profile;
    }

    private Integer nextVersionNo(String userId, String profileId) {
        return resumeVersionMapper.selectList(
                        Wrappers.lambdaQuery(ResumeVersionDO.class)
                                .eq(ResumeVersionDO::getUserId, userId)
                                .eq(ResumeVersionDO::getProfileId, profileId)
                                .eq(ResumeVersionDO::getDeleted, 0)
                ).stream()
                .map(ResumeVersionDO::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private ExportPayload buildExportPayload(ResumeVersionDO version, String exportType) {
        String markdown = firstNotBlank(version.getMarkdownContent(), version.getContentJson());
        if (StrUtil.isBlank(markdown)) {
            throw new ClientException("Resume export content is empty");
        }
        if (EXPORT_TYPE_HTML.equals(exportType)) {
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>"
                    + escapeHtml(firstNotBlank(version.getTitle(), "Resume"))
                    + "</title></head><body><pre>"
                    + escapeHtml(markdown)
                    + "</pre></body></html>";
            return new ExportPayload(
                    ("resume-" + version.getId() + ".html"),
                    "text/html",
                    html.getBytes(StandardCharsets.UTF_8)
            );
        }
        return new ExportPayload(
                ("resume-" + version.getId() + ".md"),
                "text/markdown",
                markdown.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeExportType(String exportType) {
        if (StrUtil.isBlank(exportType)) {
            throw new ClientException("Resume export type is required");
        }
        return exportType.trim().toUpperCase(Locale.ROOT);
    }

    private ResumeVersionDO requireVersion(String versionId) {
        String userId = requireUserId();
        if (StrUtil.isBlank(versionId)) {
            throw new ClientException("Resume version id is required");
        }
        ResumeVersionDO version = resumeVersionMapper.selectOne(
                Wrappers.lambdaQuery(ResumeVersionDO.class)
                        .eq(ResumeVersionDO::getId, versionId)
                        .eq(ResumeVersionDO::getUserId, userId)
                        .eq(ResumeVersionDO::getDeleted, 0)
        );
        if (version == null) {
            throw new ClientException("Resume version does not exist");
        }
        return version;
    }

    private List<String> invalidateExportRecords(String resumeVersionId, String userId) {
        List<ResumeExportRecordDO> exportRecords = resumeExportRecordMapper.selectList(
                Wrappers.lambdaQuery(ResumeExportRecordDO.class)
                        .eq(ResumeExportRecordDO::getResumeVersionId, resumeVersionId)
                        .eq(ResumeExportRecordDO::getUserId, userId)
                        .eq(ResumeExportRecordDO::getDeleted, 0)
        );
        if (exportRecords == null) {
            return List.of();
        }
        List<String> fileUrls = new ArrayList<>();
        for (ResumeExportRecordDO exportRecord : exportRecords) {
            if (StrUtil.isNotBlank(exportRecord.getFileUrl())) {
                fileUrls.add(exportRecord.getFileUrl());
            }
            resumeExportRecordMapper.deleteById(exportRecord.getId());
        }
        return fileUrls;
    }

    private void deleteExportFilesAfterDbWork(List<String> fileUrls) {
        if (fileUrls.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    deleteExportFiles(fileUrls);
                }
            });
            return;
        }
        deleteExportFiles(fileUrls);
    }

    private void deleteExportFiles(List<String> fileUrls) {
        for (String fileUrl : fileUrls) {
            try {
                fileStorageService.deleteByUrl(fileUrl);
            } catch (RuntimeException ex) {
                log.warn("Failed to delete invalidated resume export file: {}", fileUrl, ex);
            }
        }
    }

    private boolean cleanupUploadedExport(String fileUrl, RuntimeException cause) {
        if (StrUtil.isBlank(fileUrl)) {
            return true;
        }
        try {
            fileStorageService.deleteByUrl(fileUrl);
            return true;
        } catch (RuntimeException deleteEx) {
            cause.addSuppressed(deleteEx);
            return false;
        }
    }

    private CareerResumeVersionVO toVersionVO(ResumeVersionDO version) {
        return CareerResumeVersionVO.builder()
                .id(version.getId())
                .profileId(version.getProfileId())
                .versionNo(version.getVersionNo())
                .title(version.getTitle())
                .content(version.getContentJson())
                .markdownContent(version.getMarkdownContent())
                .createTime(version.getCreateTime())
                .build();
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ServiceException("Failed to serialize resume JSON");
        }
    }

    private String normalizeContentJson(String contentJson) {
        if (StrUtil.isBlank(contentJson)) {
            throw new ClientException("Resume content JSON is required");
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(contentJson));
        } catch (Exception ex) {
            throw new ClientException("Resume content JSON is invalid");
        }
    }

    private String resolveFileType(MultipartFile file) {
        String fileType = FileTypeDetector.detectType(file.getOriginalFilename(), file.getContentType());
        if (StrUtil.isBlank(fileType)) {
            return null;
        }
        return fileType.length() > 32 ? fileType.substring(0, 32) : fileType;
    }

    private String defaultTitle(CandidateProfileDO profile) {
        if (StrUtil.isNotBlank(profile.getDisplayName())) {
            return profile.getDisplayName() + " Resume";
        }
        return "Parsed Resume";
    }

    private String extractBasicValue(Map<String, Object> resumeJson, String key) {
        Object basic = resumeJson.get("basic");
        if (!(basic instanceof Map<?, ?> basicMap)) {
            return null;
        }
        return extractString(basicMap.get(key));
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNotBlank(String preferred, String fallback) {
        return StrUtil.isNotBlank(preferred) ? preferred : fallback;
    }

    private String buildSingleFlightKey(String scene, String userId, String artifactId, String prompt) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "CAREER_AI"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"),
                StrUtil.blankToDefault(prompt, ""));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String trimError(RuntimeException ex) {
        String message = ex.getMessage();
        if (StrUtil.isBlank(message)) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record ExportPayload(String fileName, String contentType, byte[] content) {
    }
}
