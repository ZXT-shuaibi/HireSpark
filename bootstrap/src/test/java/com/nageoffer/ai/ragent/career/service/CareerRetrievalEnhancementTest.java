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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerHydeQueryGenerator;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancementServiceImpl;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEvidence;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEvidenceType;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalScenario;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerRetrievalEnhancementTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void alignmentEnhancementUsesGeneratedHydeProfileForQueryOnlyEvidenceAndOverRetrieval() {
        RetrieverService retrieverService = mock(RetrieverService.class);
        RerankService rerankService = mock(RerankService.class);
        CareerHydeQueryGenerator hydeQueryGenerator = mock(CareerHydeQueryGenerator.class);
        when(hydeQueryGenerator.generate(any(), any(), any(), anyString()))
                .thenReturn("LLM 生成的目标岗位理想候选人画像，强调 Spring Boot、RAG 和 Redis 项目证据。");
        when(retrieverService.retrieve(any(RetrieveRequest.class))).thenReturn(List.of(
                RetrievedChunk.builder().id("chunk-1").text("Spring Boot interview depth").score(0.81F).build(),
                RetrievedChunk.builder().id("chunk-2").text("RAG project evidence").score(0.72F).build()
        ));
        when(rerankService.rerank(anyString(), anyList(), anyInt())).thenAnswer(invocation -> List.of(
                RetrievedChunk.builder().id("chunk-2").text("RAG project evidence").score(0.92F).build()
        ));

        CareerRetrievalEnhancement result = newService(retrieverService, rerankService, hydeQueryGenerator)
                .enhanceAlignment(resumeVersion(), jobDescription());

        assertEquals("LLM 生成的目标岗位理想候选人画像，强调 Spring Boot、RAG 和 Redis 项目证据。", result.hydeQuery());
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.HYDE_QUERY
                        && item.queryOnly()
                        && result.hydeQuery().equals(item.text())));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.RESUME_TEXT));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.JD_TEXT));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.KNOWLEDGE_CHUNK
                        && "chunk-2".equals(item.sourceId())));
        ArgumentCaptor<RetrieveRequest> retrieveRequestCaptor = ArgumentCaptor.forClass(RetrieveRequest.class);
        ArgumentCaptor<Integer> rerankLimitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(hydeQueryGenerator).generate(eq(CareerRetrievalScenario.ALIGNMENT), any(), any(),
                contains("Ideal candidate evidence"));
        verify(retrieverService).retrieve(retrieveRequestCaptor.capture());
        verify(rerankService).rerank(eq(result.hydeQuery()), anyList(), rerankLimitCaptor.capture());
        assertEquals(result.hydeQuery(), retrieveRequestCaptor.getValue().getQuery());
        assertEquals(12, retrieveRequestCaptor.getValue().getTopK());
        assertEquals(4, rerankLimitCaptor.getValue());
    }

    @Test
    void promptPayloadCarriesEvidenceTypesWithoutMakingHydeAResumeFact() throws Exception {
        CareerHydeQueryGenerator hydeQueryGenerator = mock(CareerHydeQueryGenerator.class);
        when(hydeQueryGenerator.generate(any(), any(), any(), anyString()))
                .thenReturn("理想候选人画像：具备 Redis 高并发治理经验。");

        CareerRetrievalEnhancement result = newService(null, null, hydeQueryGenerator)
                .enhanceOptimization(resumeVersion(), jobDescription(), "{\"gaps\":[\"Redis\"]}");

        Map<String, Object> payload = roundTrip(result.toPromptPayload());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) payload.get("evidence");
        Map<String, Object> hyde = evidence.stream()
                .filter(item -> "HYDE_QUERY".equals(item.get("type")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> resume = evidence.stream()
                .filter(item -> "RESUME_TEXT".equals(item.get("type")))
                .findFirst()
                .orElseThrow();

        assertEquals(Boolean.TRUE, hyde.get("queryOnly"));
        assertEquals(Boolean.FALSE, resume.get("queryOnly"));
        assertEquals("理想候选人画像：具备 Redis 高并发治理经验。", hyde.get("text"));
        assertFalse(String.valueOf(resume.get("text")).contains("理想候选人画像"));
    }

    @Test
    void generationFailureFallsBackToSeedAndKeepsLocalEvidence() {
        CareerHydeQueryGenerator hydeQueryGenerator = mock(CareerHydeQueryGenerator.class);
        when(hydeQueryGenerator.generate(any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("LLM unavailable"));

        CareerRetrievalEnhancement result = newService(null, null, hydeQueryGenerator)
                .enhanceOptimization(resumeVersion(), jobDescription(), "{\"gaps\":[\"Redis\"]}");

        assertTrue(result.hydeQuery().contains("Resume gap evidence and truthful improvement references"));
        assertTrue(result.hydeQuery().contains("Redis"));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.HYDE_QUERY
                        && item.queryOnly()
                        && result.hydeQuery().equals(item.text())));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.RESUME_TEXT
                        && item.text().contains("Java")
                        && !item.queryOnly()));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.JD_TEXT
                        && item.text().contains("Spring Boot")
                        && !item.queryOnly()));
    }

    @Test
    void blankGeneratedHydeFallsBackToSeed() {
        CareerHydeQueryGenerator hydeQueryGenerator = mock(CareerHydeQueryGenerator.class);
        when(hydeQueryGenerator.generate(any(), any(), any(), anyString())).thenReturn("   ");

        CareerRetrievalEnhancement result = newService(null, null, hydeQueryGenerator)
                .enhanceInterview(resumeVersion(), jobDescription(), "请深挖 RAG 项目");

        assertTrue(result.hydeQuery().contains("Interview question depth evidence"));
        assertTrue(result.hydeQuery().contains("请深挖 RAG 项目"));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.HYDE_QUERY && item.queryOnly()));
    }

    @Test
    void hydeGeneratorBuildsDifferentPromptsForThreeScenarios() {
        CareerSingleFlightLlmService singleFlightLlmService = mock(CareerSingleFlightLlmService.class);
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("候选人画像");
        CareerHydeQueryGenerator generator = new CareerHydeQueryGenerator(singleFlightLlmService);

        generator.generate(CareerRetrievalScenario.ALIGNMENT, resumeVersion(), jobDescription(), "alignment seed");
        generator.generate(CareerRetrievalScenario.OPTIMIZATION, resumeVersion(), jobDescription(), "optimization seed");
        generator.generate(CareerRetrievalScenario.INTERVIEW, resumeVersion(), jobDescription(), "interview seed");

        ArgumentCaptor<ChatRequest> chatRequestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(singleFlightLlmService, times(3)).chat(eq("CAREER_HYDE"), anyString(), anyString(),
                chatRequestCaptor.capture());
        List<String> prompts = chatRequestCaptor.getAllValues().stream()
                .map(request -> request.getMessages().get(0).getContent())
                .toList();
        assertTrue(prompts.get(0).contains("JD 对齐"));
        assertTrue(prompts.get(1).contains("简历优化"));
        assertTrue(prompts.get(2).contains("面试"));
        assertNotEquals(prompts.get(0), prompts.get(1));
        assertNotEquals(prompts.get(1), prompts.get(2));
    }

    @Test
    void hydeGeneratorSingleFlightKeyChangesWhenResumeContentChanges() {
        CareerSingleFlightLlmService singleFlightLlmService = mock(CareerSingleFlightLlmService.class);
        when(singleFlightLlmService.chat(anyString(), anyString(), any(), any(ChatRequest.class)))
                .thenReturn("候选人画像");
        CareerHydeQueryGenerator generator = new CareerHydeQueryGenerator(singleFlightLlmService);
        ResumeVersionDO updatedResume = resumeVersion();
        updatedResume.setContentJson("{\"skills\":[\"Java\",\"Redis\"],\"projects\":[\"Ragent v2\"]}");

        generator.generate(CareerRetrievalScenario.ALIGNMENT, resumeVersion(), jobDescription(), "alignment seed");
        generator.generate(CareerRetrievalScenario.ALIGNMENT, updatedResume, jobDescription(), "alignment seed");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(singleFlightLlmService, times(2)).chat(eq("CAREER_HYDE"), keyCaptor.capture(), anyString(),
                any(ChatRequest.class));
        assertNotEquals(keyCaptor.getAllValues().get(0), keyCaptor.getAllValues().get(1));
    }

    private CareerRetrievalEnhancementServiceImpl newService(RetrieverService retrieverService,
                                                             RerankService rerankService) {
        return newService(retrieverService, rerankService, null);
    }

    private CareerRetrievalEnhancementServiceImpl newService(RetrieverService retrieverService,
                                                             RerankService rerankService,
                                                             CareerHydeQueryGenerator hydeQueryGenerator) {
        return new CareerRetrievalEnhancementServiceImpl(
                provider(retrieverService),
                provider(rerankService),
                provider(hydeQueryGenerator));
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }

    private ResumeVersionDO resumeVersion() {
        return ResumeVersionDO.builder()
                .id("resume-1")
                .contentJson("{\"skills\":[\"Java\"],\"projects\":[\"Ragent\"]}")
                .build();
    }

    private JobDescriptionDO jobDescription() {
        return JobDescriptionDO.builder()
                .id("jd-1")
                .parsedJson("{\"requiredSkills\":[\"Spring Boot\",\"RAG\",\"Redis\"]}")
                .build();
    }

    private Map<String, Object> roundTrip(Map<String, Object> payload) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsString(payload), new TypeReference<>() {
        });
    }
}
