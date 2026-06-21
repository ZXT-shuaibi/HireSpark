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
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 层次化检索引擎。
 *
 * <p>两阶段检索：
 * <ol>
 *   <li><b>粗筛</b>：先用较小 topK 检索，从结果中提取 doc_id 候选集</li>
 *   <li><b>精检</b>：在候选 doc_id 范围内用较大 topK 检索完整 chunk</li>
 * </ol>
 *
 * <p>粗筛滤掉了大量无关文档，精检时搜索空间缩小，召回精度更高。
 */
@Slf4j
@Component
public class HierarchicalRetrievalService {

    private static final int COARSE_TOP_K = 6;
    private static final int FINE_TOP_K = 12;

    private final ObjectProvider<RetrieverService> retrieverServiceProvider;

    public HierarchicalRetrievalService(ObjectProvider<RetrieverService> retrieverServiceProvider) {
        this.retrieverServiceProvider = retrieverServiceProvider;
    }

    /**
     * 执行层次化检索：粗筛候选文档 → 精检完整 chunk。
     *
     * @param query 查询文本
     * @return 精检后的 chunk 列表
     */
    public List<RetrievedChunk> search(String query) {
        RetrieverService retrieverService = retrieverServiceProvider.getIfAvailable();
        if (retrieverService == null || StrUtil.isBlank(query)) {
            return List.of();
        }

        // Phase 1: 粗筛 —— 用较小 topK 快速锁定候选文档
        List<RetrievedChunk> coarse = retrieverService.retrieve(RetrieveRequest.builder()
                .query(query)
                .topK(COARSE_TOP_K)
                .build());

        if (CollUtil.isEmpty(coarse)) {
            log.debug("层次化粗筛无结果");
            return List.of();
        }

        // 提取候选 doc_id
        Set<String> candidateDocIds = coarse.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .limit(COARSE_TOP_K)
                .collect(Collectors.toSet());

        if (candidateDocIds.isEmpty()) {
            log.debug("层次化粗筛未提取到 doc_id，回退全量检索");
            return retrieverService.retrieve(RetrieveRequest.builder()
                    .query(query)
                    .topK(FINE_TOP_K)
                    .build());
        }

        // Phase 2: 精检 —— 在候选 doc_id 范围内检索
        String idList = candidateDocIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        String filterExpr = "metadata[\"doc_id\"] in [" + idList + "]";

        List<RetrievedChunk> fine = retrieverService.retrieve(RetrieveRequest.builder()
                .query(query)
                .topK(FINE_TOP_K)
                .filterExpr(filterExpr)
                .build());

        log.info("层次化检索完成: 粗筛 {} hits → {} candidates → 精检 {} chunks",
                coarse.size(), candidateDocIds.size(),
                fine == null ? 0 : fine.size());

        return CollUtil.isEmpty(fine) ? coarse : fine;
    }
}
