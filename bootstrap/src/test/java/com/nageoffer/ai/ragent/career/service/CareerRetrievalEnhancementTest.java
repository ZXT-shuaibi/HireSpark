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
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancement;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancementServiceImpl;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEvidence;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEvidenceType;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerRetrievalEnhancementTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void alignmentEnhancementMarksHydeAsQueryOnlyAndReranksKnowledgeChunks() {
        RetrieverService retrieverService = mock(RetrieverService.class);
        RerankService rerankService = mock(RerankService.class);
        when(retrieverService.retrieve(any(RetrieveRequest.class))).thenReturn(List.of(
                RetrievedChunk.builder().id("chunk-1").text("Spring Boot interview depth").score(0.81F).build(),
                RetrievedChunk.builder().id("chunk-2").text("RAG project evidence").score(0.72F).build()
        ));
        when(rerankService.rerank(anyString(), anyList(), anyInt())).thenAnswer(invocation -> List.of(
                RetrievedChunk.builder().id("chunk-2").text("RAG project evidence").score(0.92F).build()
        ));

        CareerRetrievalEnhancement result = newService(retrieverService, rerankService)
                .enhanceAlignment(resumeVersion(), jobDescription());

        assertFalse(result.hydeQuery().isBlank());
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.HYDE_QUERY && item.queryOnly()));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.RESUME_TEXT));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.JD_TEXT));
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.type() == CareerRetrievalEvidenceType.KNOWLEDGE_CHUNK
                        && "chunk-2".equals(item.sourceId())));
        verify(retrieverService).retrieve(any(RetrieveRequest.class));
        verify(rerankService).rerank(anyString(), anyList(), anyInt());
    }

    @Test
    void promptPayloadCarriesEvidenceTypesWithoutMakingHydeAResumeFact() throws Exception {
        CareerRetrievalEnhancement result = newService(null, null)
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
        assertFalse(String.valueOf(resume.get("text")).contains(String.valueOf(hyde.get("text"))));
    }

    private CareerRetrievalEnhancementServiceImpl newService(RetrieverService retrieverService,
                                                             RerankService rerankService) {
        return new CareerRetrievalEnhancementServiceImpl(provider(retrieverService), provider(rerankService));
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
