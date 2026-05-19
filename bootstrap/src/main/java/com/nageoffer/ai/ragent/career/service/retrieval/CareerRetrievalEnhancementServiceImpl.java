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

package com.nageoffer.ai.ragent.career.service.retrieval;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerRetrievalEnhancementServiceImpl implements CareerRetrievalEnhancementService {

    private static final int LOCAL_TEXT_MAX_LENGTH = 1600;
    private static final int QUERY_MAX_LENGTH = 1200;
    private static final int TOP_N = 4;
    private static final int OVER_RETRIEVAL_MULTIPLIER = 3;

    private final ObjectProvider<RetrieverService> retrieverServiceProvider;
    private final ObjectProvider<RerankService> rerankServiceProvider;
    private final ObjectProvider<CareerHydeQueryGenerator> hydeQueryGeneratorProvider;

    @Override
    public CareerRetrievalEnhancement enhanceAlignment(ResumeVersionDO resumeVersion, JobDescriptionDO job) {
        return enhance(CareerRetrievalScenario.ALIGNMENT, resumeVersion, job,
                "Ideal candidate evidence for target JD: " + defaultText(job == null ? null : job.getParsedJson()));
    }

    @Override
    public CareerRetrievalEnhancement enhanceOptimization(ResumeVersionDO resumeVersion,
                                                         JobDescriptionDO job,
                                                         String alignmentJson) {
        return enhance(CareerRetrievalScenario.OPTIMIZATION, resumeVersion, job,
                "Resume gap evidence and truthful improvement references: "
                        + defaultText(alignmentJson) + " " + defaultText(job == null ? null : job.getParsedJson()));
    }

    @Override
    public CareerRetrievalEnhancement enhanceInterview(ResumeVersionDO resumeVersion,
                                                      JobDescriptionDO job,
                                                      String question) {
        return enhance(CareerRetrievalScenario.INTERVIEW, resumeVersion, job,
                "Interview question depth evidence: " + defaultText(question) + " "
                        + defaultText(job == null ? null : job.getParsedJson()));
    }

    private CareerRetrievalEnhancement enhance(CareerRetrievalScenario scenario,
                                               ResumeVersionDO resumeVersion,
                                               JobDescriptionDO job,
                                               String querySeed) {
        String hydeQuery = buildHydeQuery(scenario, resumeVersion, job, querySeed);
        List<CareerRetrievalEvidence> evidence = new ArrayList<>();
        evidence.add(CareerRetrievalEvidence.builder()
                .type(CareerRetrievalEvidenceType.HYDE_QUERY)
                .sourceId(scenario.name())
                .text(hydeQuery)
                .score(1F)
                .queryOnly(true)
                .build());
        evidence.add(CareerRetrievalEvidence.builder()
                .type(CareerRetrievalEvidenceType.RESUME_TEXT)
                .sourceId(resumeVersion == null ? null : resumeVersion.getId())
                .text(limitText(resumeVersion == null ? null : resumeVersion.getContentJson(), LOCAL_TEXT_MAX_LENGTH))
                .score(1F)
                .queryOnly(false)
                .build());
        evidence.add(CareerRetrievalEvidence.builder()
                .type(CareerRetrievalEvidenceType.JD_TEXT)
                .sourceId(job == null ? null : job.getId())
                .text(limitText(job == null ? null : job.getParsedJson(), LOCAL_TEXT_MAX_LENGTH))
                .score(1F)
                .queryOnly(false)
                .build());
        evidence.addAll(retrieveKnowledge(hydeQuery));
        return new CareerRetrievalEnhancement(scenario, hydeQuery, evidence);
    }

    /**
     * 优先使用 LLM 生成的 HyDE 虚拟画像；生成器缺失、异常或空白时降级到原始 query seed。
     */
    private String buildHydeQuery(CareerRetrievalScenario scenario,
                                  ResumeVersionDO resumeVersion,
                                  JobDescriptionDO job,
                                  String querySeed) {
        String seedQuery = limitText(querySeed, QUERY_MAX_LENGTH);
        CareerHydeQueryGenerator hydeQueryGenerator = hydeQueryGeneratorProvider.getIfAvailable();
        if (hydeQueryGenerator == null) {
            return seedQuery;
        }
        try {
            String generatedQuery = limitText(
                    hydeQueryGenerator.generate(scenario, resumeVersion, job, seedQuery),
                    QUERY_MAX_LENGTH);
            return StrUtil.isBlank(generatedQuery) ? seedQuery : generatedQuery;
        } catch (RuntimeException ex) {
            log.warn("Career HyDE query generation failed, degraded to seed", ex);
            return seedQuery;
        }
    }

    private List<CareerRetrievalEvidence> retrieveKnowledge(String hydeQuery) {
        RetrieverService retrieverService = retrieverServiceProvider.getIfAvailable();
        if (retrieverService == null || StrUtil.isBlank(hydeQuery)) {
            return List.of();
        }
        try {
            List<RetrievedChunk> chunks = retrieverService.retrieve(RetrieveRequest.builder()
                    .query(hydeQuery)
                    .topK(TOP_N * OVER_RETRIEVAL_MULTIPLIER)
                    .build());
            if (chunks == null || chunks.isEmpty()) {
                return List.of();
            }
            RerankService rerankService = rerankServiceProvider.getIfAvailable();
            List<RetrievedChunk> ranked = rerankService == null
                    ? chunks.stream().limit(TOP_N).toList()
                    : rerankService.rerank(hydeQuery, chunks, TOP_N);
            if (ranked == null) {
                return List.of();
            }
            return ranked.stream()
                    .map(chunk -> CareerRetrievalEvidence.builder()
                            .type(CareerRetrievalEvidenceType.KNOWLEDGE_CHUNK)
                            .sourceId(chunk.getId())
                            .text(limitText(chunk.getText(), LOCAL_TEXT_MAX_LENGTH))
                            .score(chunk.getScore())
                            .queryOnly(false)
                            .build())
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Career retrieval enhancement degraded to local evidence", ex);
            return List.of();
        }
    }

    private String defaultText(String text) {
        return StrUtil.blankToDefault(text, "");
    }

    private String limitText(String text, int maxLength) {
        String value = StrUtil.blankToDefault(text, "").trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
