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

package com.nageoffer.ai.ragent.framework.retrieve;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 独立的 BM25 关键词相关性计算器。
 *
 * <p>用于对向量检索召回的候选结果做关键词级别的重排序，
 * 弥补向量检索在精确关键词匹配上的不足。
 *
 * <p>使用方式：
 * <pre>
 * Map&lt;String, Double&gt; scores = Bm25Scorer.score(texts, query);
 * </pre>
 */
public final class Bm25Scorer {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private Bm25Scorer() {
    }

    /**
     * 计算每个文本相对于查询的 BM25 分数。
     *
     * @param texts 候选文本列表
     * @param query 查询文本
     * @return key=文本, value=BM25分数（越高越相关）
     */
    public static Map<String, Double> score(List<String> texts, String query) {
        if (texts == null || texts.isEmpty() || query == null || query.isBlank()) {
            return Map.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Map.of();
        }

        // 计算各文档的词频
        List<Map<String, Integer>> docTermFreqs = new ArrayList<>();
        Map<String, Integer> docFreq = new HashMap<>();
        int docCount = 0;

        for (String text : texts) {
            if (text == null) continue;
            List<String> tokens = tokenize(text);
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
            }
            docTermFreqs.add(tf);
            for (String token : tf.keySet()) {
                docFreq.merge(token, 1, Integer::sum);
            }
            docCount++;
        }

        if (docCount == 0) {
            return Map.of();
        }

        // 计算平均文档长度
        double avgDocLen = texts.stream()
                .filter(Objects::nonNull)
                .mapToInt(t -> tokenize(t).size())
                .average()
                .orElse(1.0);

        // 计算每个文档的 BM25 分
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < docCount; i++) {
            String text = texts.get(i);
            if (text == null) continue;
            Map<String, Integer> tf = docTermFreqs.get(i);
            int docLen = tokenize(text).size();
            double score = 0.0;

            for (String qt : queryTokens) {
                int f = tf.getOrDefault(qt, 0);
                if (f == 0) continue;
                int df = docFreq.getOrDefault(qt, 0);
                double idf = Math.log(1.0 + (docCount - df + 0.5) / (df + 0.5));
                double numerator = f * (K1 + 1);
                double denominator = f + K1 * (1 - B + B * docLen / avgDocLen);
                score += idf * numerator / denominator;
            }
            result.put(text, score);
        }

        return result;
    }

    /**
     * 简单中文/英文分词：按非字母非数字非中文字符切分。
     */
    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || isCjk(ch)) {
                current.append(ch);
            } else {
                if (current.length() > 0) {
                    tokens.add(current.toString().toLowerCase(Locale.ROOT));
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString().toLowerCase(Locale.ROOT));
        }
        // 过滤单字噪声
        return tokens.stream()
                .filter(t -> t.length() >= 2 || (t.length() == 1 && Character.isLetter(t.charAt(0))))
                .collect(Collectors.toList());
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }
}
