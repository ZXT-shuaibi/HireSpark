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

package com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.retrieve.Bm25Scorer;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BM25 关键词重排后置处理器。
 *
 * <p>对去重后的检索结果做 BM25 关键词相关性计算，按分数降序重排。
 * 排在去重后、RRF 融合前。
 */
@Slf4j
@Component
@Order(2)
public class Bm25PostProcessor implements SearchResultPostProcessor {

    @Override
    public String getName() {
        return "BM25 Keyword Rerank";
    }

    @Override
    public int getOrder() {
        return 2; // 去重(1) → BM25(2) → RRF(3) → Rerank(10)
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        String query = context.getMainQuestion();
        if (query == null || query.isBlank()) {
            log.debug("检索查询为空，跳过 BM25 重排");
            return chunks;
        }

        List<String> texts = chunks.stream()
                .map(RetrievedChunk::getText)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, Double> scores = Bm25Scorer.score(texts, query);
        if (scores.isEmpty()) {
            log.debug("BM25 分数为空，保持原顺序");
            return chunks;
        }

        // 将 BM25 分数归一化后合并到 Chunk 的原始分数中
        double maxBm25 = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        List<RetrievedChunk> reranked = chunks.stream()
                .map(chunk -> {
                    double bm25 = scores.getOrDefault(chunk.getText(), 0.0);
                    double normalized = maxBm25 > 0 ? bm25 / maxBm25 : 0;
                    // 融合：原始向量分(0.6) + BM25归一化分(0.4)
                    chunk.setScore((float) (chunk.getScore() * 0.6 + normalized * 0.4));
                    return chunk;
                })
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .collect(Collectors.toList());

        log.info("BM25 重排完成: {} → {} chunks, maxBm25={}", chunks.size(), reranked.size(),
                String.format("%.2f", maxBm25));
        return reranked;
    }
}
