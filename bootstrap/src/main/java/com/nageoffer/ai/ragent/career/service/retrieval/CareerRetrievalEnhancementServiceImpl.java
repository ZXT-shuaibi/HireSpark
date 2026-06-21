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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CareerRetrievalEnhancementServiceImpl implements CareerRetrievalEnhancementService {

    private static final int LOCAL_TEXT_MAX_LENGTH = 1600;
    private static final int QUERY_MAX_LENGTH = 1200;
    private static final int TOP_N = 4;
    private static final int OVER_RETRIEVAL_MULTIPLIER = 3;
    private static final String CACHE_PREFIX_HYDE = "career:hyde:";
    private static final String CACHE_PREFIX_MQ = "career:mq:";
    private static final long CACHE_TTL_HOURS = 24;

    private final ObjectProvider<RetrieverService> retrieverServiceProvider;
    private final ObjectProvider<RerankService> rerankServiceProvider;
    private final ObjectProvider<CareerHydeQueryGenerator> hydeQueryGeneratorProvider;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<HierarchicalRetrievalService> hierarchicalRetrievalProvider;

    public CareerRetrievalEnhancementServiceImpl(
            ObjectProvider<RetrieverService> retrieverServiceProvider,
            ObjectProvider<RerankService> rerankServiceProvider,
            ObjectProvider<CareerHydeQueryGenerator> hydeQueryGeneratorProvider,
            ObjectProvider<LLMService> llmServiceProvider,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectProvider<HierarchicalRetrievalService> hierarchicalRetrievalProvider) {
        this.retrieverServiceProvider = retrieverServiceProvider;
        this.rerankServiceProvider = rerankServiceProvider;
        this.hydeQueryGeneratorProvider = hydeQueryGeneratorProvider;
        this.llmServiceProvider = llmServiceProvider;
        this.redisProvider = redisProvider;
        this.hierarchicalRetrievalProvider = hierarchicalRetrievalProvider;
    }

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
        String hydeQuery = buildHydeQueryWithCache(scenario, resumeVersion, job, querySeed);
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
            // 多查询生成：原始 HyDE + 技术栈/行业/通用 3 个变体（带 Redis 缓存）
            List<String> allQueries = new ArrayList<>();
            allQueries.add(hydeQuery);
            allQueries.addAll(generateMultiQueriesWithCache(hydeQuery));

            // 每个查询独立检索（层次化：粗筛→精检）+ 去重合并
            HierarchicalRetrievalService hierarchical = hierarchicalRetrievalProvider.getIfAvailable();
            Set<String> seenIds = new HashSet<>();
            List<RetrievedChunk> allChunks = new ArrayList<>();
            for (String query : allQueries) {
                List<RetrievedChunk> result = hierarchical != null
                        ? hierarchical.search(query)
                        : retrieverService.retrieve(RetrieveRequest.builder()
                                .query(query)
                                .topK(TOP_N * OVER_RETRIEVAL_MULTIPLIER)
                                .build());
                if (CollUtil.isNotEmpty(result)) {
                    for (RetrievedChunk chunk : result) {
                        if (seenIds.add(chunkKey(chunk))) {
                            allChunks.add(chunk);
                        }
                    }
                }
            }

            if (allChunks.isEmpty()) {
                return List.of();
            }
            RerankService rerankService = rerankServiceProvider.getIfAvailable();
            List<RetrievedChunk> ranked = rerankService == null
                    ? allChunks.stream().limit(TOP_N).toList()
                    : rerankService.rerank(hydeQuery, allChunks, TOP_N);
            if (ranked == null) {
                return List.of();
            }
            log.info("Career 检索增强完成: {} queries → {} unique chunks → {} after rerank",
                    allQueries.size(), allChunks.size(), ranked.size());
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

    /**
     * 多查询生成（带 Redis 缓存）—— 用 LLM 生成技术栈、行业经验、通用表达三个角度的查询变体。
     * 失败时降级为空列表（只保留原始 HyDE 查询）。
     */
    private List<String> generateMultiQueriesWithCache(String hydeQuery) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            String cacheKey = CACHE_PREFIX_MQ + sha256(hydeQuery);
            String cached = redis.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                log.debug("MultiQuery 命中 Redis 缓存");
                return Arrays.stream(cached.split("\\|")).filter(StrUtil::isNotBlank).collect(Collectors.toList());
            }
        }
        List<String> queries = generateMultiQueriesRaw(hydeQuery);
        if (redis != null && !queries.isEmpty()) {
            String cacheKey = CACHE_PREFIX_MQ + sha256(hydeQuery);
            redis.opsForValue().set(cacheKey, String.join("|", queries), CACHE_TTL_HOURS, TimeUnit.HOURS);
        }
        return queries;
    }

    private List<String> generateMultiQueriesRaw(String hydeQuery) {
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null || StrUtil.isBlank(hydeQuery)) {
            return List.of();
        }
        try {
            String prompt = "请针对以下简历检索查询，生成3个不同角度的变体查询语句（每个一行，不要编号）：\n"
                    + "1. 技术栈和硬技能角度\n2. 行业经验和业务领域角度\n3. 通用表达和同义词角度\n\n"
                    + "原始查询：\n" + limitText(hydeQuery, 800);
            String response = llmService.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.1)
                    .maxTokens(300)
                    .thinking(false)
                    .build());
            if (StrUtil.isBlank(response)) {
                return List.of();
            }
            return Arrays.stream(response.split("\\n"))
                    .map(String::trim)
                    .filter(s -> s.length() > 8 && !s.startsWith("1.") && !s.startsWith("2.") && !s.startsWith("3."))
                    .limit(3)
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            log.warn("多查询生成失败，降级为单查询", ex);
            return List.of();
        }
    }

    /**
     * 添加 HyDE 结果 Redis 缓存（在 buildHydeQuery 中调用）。
     */
    private String buildHydeQueryWithCache(CareerRetrievalScenario scenario,
                                           ResumeVersionDO resumeVersion,
                                           JobDescriptionDO job,
                                           String querySeed) {
        String seedQuery = limitText(querySeed, QUERY_MAX_LENGTH);
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            String cacheKey = CACHE_PREFIX_HYDE + scenario.name().toLowerCase() + ":" + sha256(seedQuery);
            String cached = redis.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                log.debug("HyDE 命中 Redis 缓存, scenario={}", scenario.name());
                return cached;
            }
        }
        String result = buildHydeQuery(scenario, resumeVersion, job, querySeed);
        if (redis != null && StrUtil.isNotBlank(result) && !result.equals(seedQuery)) {
            String cacheKey = CACHE_PREFIX_HYDE + scenario.name().toLowerCase() + ":" + sha256(seedQuery);
            redis.opsForValue().set(cacheKey, result, CACHE_TTL_HOURS, TimeUnit.HOURS);
        }
        return result;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String chunkKey(RetrievedChunk chunk) {
        return chunk.getId() != null ? chunk.getId() : String.valueOf(Objects.hash(chunk.getText()));
    }

    private String defaultText(String text) {
        return StrUtil.blankToDefault(text, "");
    }

    private String limitText(String text, int maxLength) {
        String value = StrUtil.blankToDefault(text, "").trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
