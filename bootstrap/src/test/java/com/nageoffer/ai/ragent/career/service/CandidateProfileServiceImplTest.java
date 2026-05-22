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
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeVersionVO;
import com.nageoffer.ai.ragent.career.dao.entity.CandidateProfileDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeDocumentDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CandidateProfileMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeDocumentMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeExportRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.service.impl.CandidateProfileServiceImpl;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.parser.ResumeTextExtractor;
import com.nageoffer.ai.ragent.career.service.parser.ResumeTextExtractionResult;
import com.nageoffer.ai.ragent.career.service.render.ResumeRenderPipeline;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateProfileServiceImplTest {

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
        UserContext.clear();
    }

    @Test
    void queryVersionReturnsCurrentUsersVersion() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        Date createTime = new Date();
        ResumeVersionDO version = ResumeVersionDO.builder()
                .id("version-1")
                .userId("user-1")
                .profileId("profile-1")
                .versionNo(1)
                .title("Parsed Resume")
                .contentJson("{\"basic\":{\"name\":\"Alice\"}}")
                .markdownContent("# Alice")
                .createTime(createTime)
                .build();
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(version);

        CandidateProfileServiceImpl service = newService();
        CareerResumeVersionVO result = service.queryVersion("version-1");

        assertEquals("version-1", result.getId());
        assertEquals("profile-1", result.getProfileId());
        assertEquals(1, result.getVersionNo());
        assertEquals("Parsed Resume", result.getTitle());
        assertEquals("{\"basic\":{\"name\":\"Alice\"}}", result.getContent());
        assertEquals("# Alice", result.getMarkdownContent());
        assertEquals(createTime, result.getCreateTime());

        verify(resumeVersionMapper).selectOne(anyWrapper());
    }

    @Test
    void queryVersionRejectsMissingOrForeignVersion() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(null);

        CandidateProfileServiceImpl service = newService();

        assertThrows(ClientException.class, () -> service.queryVersion("version-404"));
    }

    @Test
    void uploadNormalizesDocxMimeAndIncrementsVersionNo() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "binary".getBytes(StandardCharsets.UTF_8)
        );
        CandidateProfileDO profile = CandidateProfileDO.builder()
                .id("profile-1")
                .userId("user-1")
                .displayName("Alice")
                .build();
        when(resumeTextExtractor.extractWithMetadata(file))
                .thenReturn(new ResumeTextExtractionResult("resume text", "OCR_ENHANCED"));
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"basic\":{\"name\":\"Alice\"}}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(Map.of(
                "basic", Map.of("name", "Alice", "headline", "Java Developer"),
                "summary", "Backend engineer"
        ));
        when(candidateProfileMapper.selectOne(any())).thenReturn(profile);
        when(resumeVersionMapper.selectList(anyWrapper())).thenReturn(List.of(
                ResumeVersionDO.builder().versionNo(1).build(),
                ResumeVersionDO.builder().versionNo(3).build()
        ));
        doAnswer(invocation -> {
            ResumeDocumentDO document = invocation.getArgument(0);
            document.setId("document-1");
            return 1;
        }).when(resumeDocumentMapper).insert(any(ResumeDocumentDO.class));
        doAnswer(invocation -> {
            ResumeVersionDO version = invocation.getArgument(0);
            version.setId("version-4");
            return 1;
        }).when(resumeVersionMapper).insert(any(ResumeVersionDO.class));

        var result = newService().uploadAndParse(file);

        assertEquals("document-1", result.getDocumentId());
        assertEquals("profile-1", result.getProfileId());
        assertEquals("version-4", result.getResumeVersionId());
        assertEquals("SUCCESS", result.getParseStatus());

        ArgumentCaptor<ResumeDocumentDO> documentCaptor = ArgumentCaptor.forClass(ResumeDocumentDO.class);
        verify(resumeDocumentMapper).insert(documentCaptor.capture());
        assertEquals("docx", documentCaptor.getValue().getFileType());

        ArgumentCaptor<ResumeVersionDO> versionCaptor = ArgumentCaptor.forClass(ResumeVersionDO.class);
        verify(resumeVersionMapper).insert(versionCaptor.capture());
        assertEquals(4, versionCaptor.getValue().getVersionNo());
        org.junit.jupiter.api.Assertions.assertTrue(
                versionCaptor.getValue().getContentJson().contains("\"contentSource\":\"OCR_ENHANCED\""));
    }

    @Test
    void updateVersionRejectsInvalidContentJsonBeforePersistence() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        ResumeVersionDO version = ResumeVersionDO.builder()
                .id("version-1")
                .userId("user-1")
                .profileId("profile-1")
                .contentJson("{\"basic\":{}}")
                .build();
        when(resumeVersionMapper.selectOne(anyWrapper())).thenReturn(version);
        var request = new com.nageoffer.ai.ragent.career.controller.request.CareerResumeUpdateRequest();
        request.setContentJson("{invalid-json");

        assertThrows(ClientException.class, () -> newService().updateVersion("version-1", request));
        verify(resumeVersionMapper, never()).updateById(any(ResumeVersionDO.class));
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

}
