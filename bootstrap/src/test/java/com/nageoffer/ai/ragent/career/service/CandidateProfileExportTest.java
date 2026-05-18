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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nageoffer.ai.ragent.career.controller.request.CareerResumeExportRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeExportVO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeExportRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CandidateProfileMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeDocumentMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeExportRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.service.impl.CandidateProfileServiceImpl;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.parser.ResumeTextExtractor;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderPipeline;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateProfileExportTest {

    @Mock
    private CandidateProfileMapper candidateProfileMapper;

    @Mock
    private ResumeDocumentMapper resumeDocumentMapper;

    @Mock
    private ResumeVersionMapper resumeVersionMapper;

    @Mock
    private ResumeExportRecordMapper resumeExportRecordMapper;

    @Mock
    private ResumeTextExtractor resumeTextExtractor;

    @Mock
    private CareerJsonParser careerJsonParser;

    @Mock
    private CareerSingleFlightLlmService singleFlightLlmService;

    @Mock
    private FileStorageService fileStorageService;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        UserContext.clear();
    }

    @Test
    void exportMarkdownUploadsMarkdownContentAndRecordsSuccess() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        when(fileStorageService.upload(eq("career-resume-export"), any(byte[].class), anyString(), eq("text/markdown")))
                .thenReturn(StoredFileDTO.builder()
                        .url("s3://career-resume-export/resume.md")
                        .size(7L)
                        .detectedType("markdown")
                        .originalFilename("resume-version-1.md")
                        .build());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-1");
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));

        CareerResumeExportVO result = newService().export(exportRequest("MARKDOWN"));

        assertEquals("export-1", result.getId());
        assertEquals("version-1", result.getResumeVersionId());
        assertEquals("MARKDOWN", result.getExportType());
        assertEquals("s3://career-resume-export/resume.md", result.getFileUrl());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(ResumeRenderPipeline.TEMPLATE_VERSION, result.getTemplateVersion());
        org.assertj.core.api.Assertions.assertThat(result.getValidationResultJson())
                .contains("\"valid\":true", "\"templateVersion\":\"career-resume-template-v1\"");

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).upload(eq("career-resume-export"), contentCaptor.capture(), eq("resume-version-1.md"), eq("text/markdown"));
        assertEquals("# Alice", new String(contentCaptor.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    void exportHtmlWrapsMarkdownContent() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        when(fileStorageService.upload(eq("career-resume-export"), any(byte[].class), anyString(), eq("text/html")))
                .thenReturn(StoredFileDTO.builder().url("s3://career-resume-export/resume.html").build());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-2");
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));

        newService().export(exportRequest("HTML"));

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).upload(eq("career-resume-export"), contentCaptor.capture(), eq("resume-version-1.html"), eq("text/html"));
        String html = new String(contentCaptor.getValue(), StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("<!doctype html>", "<pre># Alice</pre>");
    }

    @Test
    void exportPdfAndWordCreateFailedRecordWithDisabledReasonBeforeUploading() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-disabled-" + record.getExportType());
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));

        CareerResumeExportVO pdf = newService().export(exportRequest("PDF"));
        CareerResumeExportVO word = newService().export(exportRequest("WORD"));

        assertEquals("FAILED", pdf.getStatus());
        assertEquals("PDF renderer is not enabled", pdf.getErrorMessage());
        assertEquals("FAILED", word.getStatus());
        assertEquals("WORD renderer is not enabled", word.getErrorMessage());
        assertEquals(ResumeRenderPipeline.TEMPLATE_VERSION, pdf.getTemplateVersion());
        verify(fileStorageService, never()).upload(anyString(), any(byte[].class), anyString(), anyString());
        verify(resumeExportRecordMapper, times(2)).insert(any(ResumeExportRecordDO.class));
        verify(resumeExportRecordMapper, times(2)).updateById(any(ResumeExportRecordDO.class));
    }

    @Test
    void exportMarksRecordFailedWhenUploadFails() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-3");
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));
        when(fileStorageService.upload(eq("career-resume-export"), any(byte[].class), anyString(), eq("text/markdown")))
                .thenThrow(new RuntimeException("upload failed"));

        assertThrows(RuntimeException.class, () -> newService().export(exportRequest("MARKDOWN")));

        ArgumentCaptor<ResumeExportRecordDO> recordCaptor = ArgumentCaptor.forClass(ResumeExportRecordDO.class);
        verify(resumeExportRecordMapper).updateById(recordCaptor.capture());
        assertEquals("FAILED", recordCaptor.getValue().getStatus());
        assertEquals("upload failed", recordCaptor.getValue().getErrorMessage());
    }

    @Test
    void exportDeletesUploadedFileWhenSuccessRecordUpdateFails() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-4");
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));
        when(fileStorageService.upload(eq("career-resume-export"), any(byte[].class), anyString(), eq("text/markdown")))
                .thenReturn(StoredFileDTO.builder().url("s3://career-resume-export/orphan.md").build());
        doThrow(new RuntimeException("db down"))
                .when(resumeExportRecordMapper).updateById(any(ResumeExportRecordDO.class));

        assertThrows(RuntimeException.class, () -> newService().export(exportRequest("MARKDOWN")));

        verify(fileStorageService).deleteByUrl("s3://career-resume-export/orphan.md");
    }

    @Test
    void exportClearsFileUrlWhenUploadedFileIsCleanedAfterSuccessRecordUpdateFails() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-5");
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));
        when(fileStorageService.upload(eq("career-resume-export"), any(byte[].class), anyString(), eq("text/markdown")))
                .thenReturn(StoredFileDTO.builder().url("s3://career-resume-export/cleaned.md").build());
        List<ResumeExportRecordDO> updateSnapshots = new ArrayList<>();
        AtomicInteger updateCount = new AtomicInteger();
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            updateSnapshots.add(ResumeExportRecordDO.builder()
                    .status(record.getStatus())
                    .fileUrl(record.getFileUrl())
                    .errorMessage(record.getErrorMessage())
                    .build());
            if (updateCount.incrementAndGet() == 1) {
                throw new RuntimeException("success update failed");
            }
            return 1;
        }).when(resumeExportRecordMapper).updateById(any(ResumeExportRecordDO.class));

        assertThrows(RuntimeException.class, () -> newService().export(exportRequest("MARKDOWN")));

        verify(fileStorageService).deleteByUrl("s3://career-resume-export/cleaned.md");
        verify(resumeExportRecordMapper, times(2)).updateById(any(ResumeExportRecordDO.class));
        assertEquals("FAILED", updateSnapshots.get(1).getStatus());
        assertNull(updateSnapshots.get(1).getFileUrl());
    }

    @Test
    void exportKeepsFileUrlWhenUploadedFileCleanupFailsAfterSuccessRecordUpdateFails() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            record.setId("export-6");
            return 1;
        }).when(resumeExportRecordMapper).insert(any(ResumeExportRecordDO.class));
        when(fileStorageService.upload(eq("career-resume-export"), any(byte[].class), anyString(), eq("text/markdown")))
                .thenReturn(StoredFileDTO.builder().url("s3://career-resume-export/not-cleaned.md").build());
        doThrow(new RuntimeException("delete failed"))
                .when(fileStorageService).deleteByUrl("s3://career-resume-export/not-cleaned.md");
        List<ResumeExportRecordDO> updateSnapshots = new ArrayList<>();
        AtomicInteger updateCount = new AtomicInteger();
        doAnswer(invocation -> {
            ResumeExportRecordDO record = invocation.getArgument(0);
            updateSnapshots.add(ResumeExportRecordDO.builder()
                    .status(record.getStatus())
                    .fileUrl(record.getFileUrl())
                    .errorMessage(record.getErrorMessage())
                    .build());
            if (updateCount.incrementAndGet() == 1) {
                throw new RuntimeException("success update failed");
            }
            return 1;
        }).when(resumeExportRecordMapper).updateById(any(ResumeExportRecordDO.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> newService().export(exportRequest("MARKDOWN")));

        verify(resumeExportRecordMapper, times(2)).updateById(any(ResumeExportRecordDO.class));
        assertEquals("success update failed", ex.getMessage());
        assertEquals(1, ex.getSuppressed().length);
        assertEquals("delete failed", ex.getSuppressed()[0].getMessage());
        assertEquals("FAILED", updateSnapshots.get(1).getStatus());
        assertEquals("s3://career-resume-export/not-cleaned.md", updateSnapshots.get(1).getFileUrl());
    }

    @Test
    void deleteVersionInvalidatesExportFilesAndRecords() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        when(resumeExportRecordMapper.selectList(anyExportRecordWrapper())).thenReturn(List.of(
                ResumeExportRecordDO.builder()
                        .id("export-1")
                        .userId("user-1")
                        .resumeVersionId("version-1")
                        .fileUrl("s3://career-resume-export/resume.md")
                        .status("SUCCESS")
                        .build(),
                ResumeExportRecordDO.builder()
                        .id("export-2")
                        .userId("user-1")
                        .resumeVersionId("version-1")
                        .fileUrl(null)
                        .status("FAILED")
                        .build()
        ));

        newService().deleteVersion("version-1");

        verify(fileStorageService).deleteByUrl("s3://career-resume-export/resume.md");
        verify(resumeExportRecordMapper).deleteById("export-1");
        verify(resumeExportRecordMapper).deleteById("export-2");
        verify(resumeVersionMapper).deleteById("version-1");
        InOrder inOrder = inOrder(resumeVersionMapper, resumeExportRecordMapper, fileStorageService);
        inOrder.verify(resumeVersionMapper).updateById(any(ResumeVersionDO.class));
        inOrder.verify(resumeExportRecordMapper).deleteById("export-1");
        inOrder.verify(resumeExportRecordMapper).deleteById("export-2");
        inOrder.verify(resumeVersionMapper).deleteById("version-1");
        inOrder.verify(fileStorageService).deleteByUrl("s3://career-resume-export/resume.md");
    }

    @Test
    void deleteVersionDefersExportFileCleanupAndContinuesWhenPostCommitCleanupFails() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(resumeVersion());
        when(resumeExportRecordMapper.selectList(anyExportRecordWrapper())).thenReturn(List.of(
                ResumeExportRecordDO.builder()
                        .id("export-1")
                        .userId("user-1")
                        .resumeVersionId("version-1")
                        .fileUrl("s3://career-resume-export/first.md")
                        .status("SUCCESS")
                        .build(),
                ResumeExportRecordDO.builder()
                        .id("export-2")
                        .userId("user-1")
                        .resumeVersionId("version-1")
                        .fileUrl("s3://career-resume-export/second.md")
                        .status("SUCCESS")
                        .build()
        ));
        doThrow(new RuntimeException("first delete failed"))
                .when(fileStorageService).deleteByUrl("s3://career-resume-export/first.md");

        newService().deleteVersion("version-1");

        verify(resumeExportRecordMapper).deleteById("export-1");
        verify(resumeExportRecordMapper).deleteById("export-2");
        verify(resumeVersionMapper).deleteById("version-1");
        verify(fileStorageService, never()).deleteByUrl(anyString());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        assertDoesNotThrow(() -> synchronizations.get(0).afterCommit());
        verify(fileStorageService).deleteByUrl("s3://career-resume-export/first.md");
        verify(fileStorageService).deleteByUrl("s3://career-resume-export/second.md");
    }

    private CareerResumeExportRequest exportRequest(String exportType) {
        CareerResumeExportRequest request = new CareerResumeExportRequest();
        request.setResumeVersionId("version-1");
        request.setExportType(exportType);
        return request;
    }

    private ResumeVersionDO resumeVersion() {
        return ResumeVersionDO.builder()
                .id("version-1")
                .userId("user-1")
                .profileId("profile-1")
                .versionNo(1)
                .title("Alice Resume")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .markdownContent("# Alice")
                .build();
    }

    private CandidateProfileServiceImpl newService() {
        return new CandidateProfileServiceImpl(
                candidateProfileMapper,
                resumeDocumentMapper,
                resumeVersionMapper,
                resumeExportRecordMapper,
                resumeTextExtractor,
                careerJsonParser,
                singleFlightLlmService,
                fileStorageService,
                new ResumeRenderPipeline()
        );
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeVersionDO> anyWrapper() {
        return (Wrapper<ResumeVersionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ResumeExportRecordDO> anyExportRecordWrapper() {
        return (Wrapper<ResumeExportRecordDO>) any(Wrapper.class);
    }
}
