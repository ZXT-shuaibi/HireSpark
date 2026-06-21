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
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RRF（Reciprocal Rank Fusion）多通道结果融合后置处理器。
 *
 * <p>对来自多个检索通道的结果用 RRF 算法融合排序：
 * 每个结果在不同通道里的排名倒数加权求和，通道排名越靠前权重越高。
 *
 * <p>排在 BM25 后、Rerank 前。
 */
@Slf4j
@Component
@Order(3)
public class RrfPostProcessor implements SearchResultPostProcessor {

    private static final int K = 60; // RRF 平滑参数

    @Override
    public String getName() {
        return "RRF Fusion";
    }

    @Override
    public int getOrder() {
        return 3; // 去重(1) → BM25(2) → RRF(3) → Rerank(10)
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (results == null || results.size() <= 1) {
            log.debug("少于 2 个通道，跳过 RRF 融合");
            return chunks;
        }

        // 对每个通道的结果按分数降序排名
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RetrievedChunk> chunkById = new LinkedHashMap<>();
        for (SearchChannelResult result : results) {
            List<RetrievedChunk> sorted = result.getChunks().stream()
                    .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                    .collect(Collectors.toList());
            for (int rank = 0; rank < sorted.size(); rank++) {
                RetrievedChunk chunk = sorted.get(rank);
                String key = chunkKey(chunk);
                double rrf = 1.0 / (K + rank + 1);
                rrfScores.merge(key, rrf, Double::sum);
                chunkById.putIfAbsent(key, chunk);
            }
        }

        if (rrfScores.isEmpty()) {
            return chunks;
        }

        // 归一化 RRF 分并排序
        double maxRrf = rrfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        List<RetrievedChunk> fused = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    RetrievedChunk chunk = chunkById.get(entry.getKey());
                    if (chunk != null) {
                        double normalized = maxRrf > 0 ? entry.getValue() / maxRrf : 0;
                        chunk.setScore((float) normalized);
                    }
                    return chunk;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("RRF 融合完成: {} 通道 → {} chunks", results.size(), fused.size());
        return fused;
    }

    private String chunkKey(RetrievedChunk chunk) {
        return chunk.getId() != null
                ? chunk.getId()
                : String.valueOf(Objects.hash(chunk.getText()));
    }
}
