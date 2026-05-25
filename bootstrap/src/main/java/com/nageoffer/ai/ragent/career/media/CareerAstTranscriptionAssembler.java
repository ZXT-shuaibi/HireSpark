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

package com.nageoffer.ai.ragent.career.media;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AST 实时转写组装器，负责把分段增量包重建为稳定的面试答案草稿。
 */
public class CareerAstTranscriptionAssembler {

    private final TreeMap<Integer, SegmentState> segments = new TreeMap<>();

    private final AtomicInteger revision = new AtomicInteger();

    /**
     * 应用一个 ASR 分段包并返回最新转写快照。
     */
    public synchronized CareerTranscriptionUpdate apply(AstTranscriptionSegment segment) {
        if (segment == null || segment.text() == null) {
            return buildUpdate(null);
        }
        int segmentId = segment.segmentId() == null ? nextSegmentId() : segment.segmentId();
        if (isBlank(segment.pgs())) {
            applyWithoutPgs(segmentId, segment.bg(), segment.ed(), segment.text(), segment.finalPacket());
        } else {
            applyWithPgs(segmentId, segment.pgs(), segment.rg(), segment.text(), segment.finalPacket());
        }
        return buildUpdate(new AstTranscriptionSegment(segmentId, segment.pgs(), segment.rg(), segment.bg(), segment.ed(),
                segment.text(), segment.finalPacket()));
    }

    /**
     * 构建当前完整显示文本。
     */
    public synchronized String buildSnapshot() {
        StringBuilder result = new StringBuilder();
        for (SegmentState part : segments.values()) {
            if (part != null && part.text != null) {
                result.append(part.text);
            }
        }
        return result.toString();
    }

    /**
     * 根据活动片段计算已确认文本。
     */
    public synchronized String buildCommittedText(int activeSegmentId, boolean finalPacket) {
        if (finalPacket) {
            return buildSnapshot();
        }
        StringBuilder committed = new StringBuilder();
        for (SegmentState segment : segments.values()) {
            if (segment == null || segment.text == null) {
                continue;
            }
            if (segment.segmentId < activeSegmentId || segment.finalized) {
                committed.append(segment.text);
            }
        }
        return committed.toString();
    }

    /**
     * 根据已确认文本计算前端需要替换的 live 文本。
     */
    public String buildLiveText(String committedText, String displayText, String segmentText, boolean finalPacket) {
        if (finalPacket || isBlank(displayText)) {
            return "";
        }
        String committed = committedText == null ? "" : committedText;
        if (!committed.isEmpty() && displayText.startsWith(committed)) {
            return displayText.substring(committed.length());
        }
        return segmentText != null ? segmentText : displayText;
    }

    /**
     * 处理没有 pgs 的 AST 包，避免把连续临时结果重复拼接。
     */
    private void applyWithoutPgs(int segmentId, Integer bg, Integer ed, String text, boolean finalized) {
        if (bg == null || ed == null) {
            removeUnfinalizedSegments();
            upsertSegment(segmentId, text, finalized, bg, ed);
            return;
        }

        SegmentState sameRange = findExactRangeState(bg, ed);
        if (sameRange != null && isPunctuationOnly(text) && !isBlank(sameRange.text)) {
            sameRange.text = appendTrailingPunctuation(sameRange.text, text);
            sameRange.finalized = sameRange.finalized || finalized;
            sameRange.updatedAt = System.currentTimeMillis();
            return;
        }

        if (!finalized) {
            removeUnfinalizedSegments();
        }
        SegmentState reusable = findReusableRangeState(bg, ed, text);
        if (reusable != null) {
            updateSegmentState(reusable, text, finalized, bg, ed);
            removeCoveredSiblingRangeStates(reusable.segmentId, bg, ed, text);
            return;
        }
        removeCoveredSiblingRangeStates(null, bg, ed, text);
        upsertSegment(segmentId, text, finalized, bg, ed);
    }

    /**
     * 处理带 pgs 的 AST 包，支持 apd 追加和 rpl 替换。
     */
    private void applyWithPgs(int segmentId, String pgs, int[] rg, String text, boolean finalized) {
        if ("rpl".equalsIgnoreCase(pgs)) {
            if (rg != null && rg.length >= 2) {
                int start = Math.min(rg[0], rg[1]);
                int end = Math.max(rg[0], rg[1]);
                for (int i = start; i <= end; i++) {
                    segments.remove(i);
                }
            }
            upsertSegment(segmentId, text, finalized, null, null);
            return;
        }
        upsertSegment(segmentId, text, finalized, null, null);
    }

    /**
     * 构建一次可推送给前端的转写更新。
     */
    private CareerTranscriptionUpdate buildUpdate(AstTranscriptionSegment segment) {
        String snapshot = buildSnapshot();
        int activeSegmentId = segment == null || segment.segmentId() == null ? nextSegmentId() : segment.segmentId();
        boolean finalPacket = segment != null && segment.finalPacket();
        String committedText = buildCommittedText(activeSegmentId, finalPacket);
        String liveText = buildLiveText(committedText, snapshot, segment == null ? null : segment.text(), finalPacket);
        return new CareerTranscriptionUpdate(
                snapshot,
                committedText,
                liveText,
                snapshot,
                revision.incrementAndGet(),
                finalPacket ? "final" : "partial",
                segment == null ? null : segment.segmentId(),
                segment == null ? null : segment.text(),
                segment == null ? null : segment.pgs(),
                segment == null ? null : segment.rg(),
                segment == null ? null : segment.bg(),
                segment == null ? null : segment.ed(),
                finalPacket
        );
    }

    /**
     * 生成下一个片段 ID。
     */
    private int nextSegmentId() {
        return segments.isEmpty() ? 1 : segments.lastKey() + 1;
    }

    /**
     * 删除尚未确认的临时片段。
     */
    private void removeUnfinalizedSegments() {
        segments.entrySet().removeIf(entry -> entry.getValue() != null && !entry.getValue().finalized);
    }

    /**
     * 查找时间范围完全一致的片段。
     */
    private SegmentState findExactRangeState(Integer bg, Integer ed) {
        for (SegmentState state : segments.values()) {
            if (state == null || state.bg == null || state.ed == null) {
                continue;
            }
            if (state.bg.equals(bg) && state.ed.equals(ed)) {
                return state;
            }
        }
        return null;
    }

    /**
     * 查找可复用的演进片段，避免前缀增量被重复拼接。
     */
    private SegmentState findReusableRangeState(Integer bg, Integer ed, String text) {
        if (bg == null || ed == null || isBlank(text)) {
            return null;
        }
        SegmentState best = null;
        double bestScore = -1D;
        for (SegmentState state : segments.values()) {
            if (state == null || state.bg == null || state.ed == null || !isRangeOverlapping(bg, ed, state.bg, state.ed)) {
                continue;
            }
            if (state.bg.equals(bg) && state.ed.equals(ed)) {
                return state;
            }
            double overlapRatio = calculateOverlapRatio(bg, ed, state.bg, state.ed);
            if (overlapRatio < 0.6D || !isLikelySameSegmentEvolution(state.text, text)) {
                continue;
            }
            double score = containsComparableText(text, state.text) ? overlapRatio + 1D : overlapRatio;
            if (score > bestScore) {
                bestScore = score;
                best = state;
            }
        }
        return best;
    }

    /**
     * 删除被新范围覆盖的同源临时片段。
     */
    private void removeCoveredSiblingRangeStates(Integer retainedSegmentId, Integer bg, Integer ed, String text) {
        if (bg == null || ed == null) {
            return;
        }
        Iterator<Map.Entry<Integer, SegmentState>> iterator = segments.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, SegmentState> entry = iterator.next();
            SegmentState state = entry.getValue();
            if (state == null || state.bg == null || state.ed == null) {
                continue;
            }
            if (retainedSegmentId != null && retainedSegmentId.equals(entry.getKey())) {
                continue;
            }
            if (isRangeFullyCoveredBy(bg, ed, state.bg, state.ed) && isLikelySameSegmentEvolution(state.text, text)) {
                iterator.remove();
            }
        }
    }

    /**
     * 更新片段状态。
     */
    private void updateSegmentState(SegmentState state, String text, boolean finalized, Integer bg, Integer ed) {
        state.text = text;
        state.finalized = state.finalized || finalized;
        state.bg = bg;
        state.ed = ed;
        state.updatedAt = System.currentTimeMillis();
    }

    /**
     * 新增或覆盖指定片段。
     */
    private void upsertSegment(int segmentId, String text, boolean finalized, Integer bg, Integer ed) {
        SegmentState state = segments.get(segmentId);
        if (state == null) {
            state = new SegmentState(segmentId);
        }
        updateSegmentState(state, text, finalized, bg, ed);
        segments.put(segmentId, state);
    }

    /**
     * 判断两个时间范围是否重叠。
     */
    private boolean isRangeOverlapping(int bg1, int ed1, int bg2, int ed2) {
        return bg1 <= ed2 && bg2 <= ed1;
    }

    /**
     * 判断内部范围是否被外部范围完全覆盖。
     */
    private boolean isRangeFullyCoveredBy(int outerBg, int outerEd, int innerBg, int innerEd) {
        return outerBg <= innerBg && outerEd >= innerEd;
    }

    /**
     * 计算两个范围的重叠比例。
     */
    private double calculateOverlapRatio(int bg1, int ed1, int bg2, int ed2) {
        int overlapStart = Math.max(bg1, bg2);
        int overlapEnd = Math.min(ed1, ed2);
        int overlap = overlapEnd - overlapStart;
        if (overlap <= 0) {
            return 0D;
        }
        int span1 = Math.max(1, ed1 - bg1);
        int span2 = Math.max(1, ed2 - bg2);
        return (double) overlap / Math.min(span1, span2);
    }

    /**
     * 判断文本是否只有标点。
     */
    private boolean isPunctuationOnly(String text) {
        if (isBlank(text)) {
            return false;
        }
        String trimmed = rtrim(text);
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                return false;
            }
        }
        return true;
    }

    /**
     * 给已有文本追加尾部标点。
     */
    private String appendTrailingPunctuation(String baseText, String punctuation) {
        String base = rtrim(baseText);
        String suffix = rtrim(punctuation);
        if (suffix.isEmpty() || base.endsWith(suffix)) {
            return base;
        }
        return base + suffix;
    }

    /**
     * 判断新旧文本是否属于同一片段演进。
     */
    private boolean isLikelySameSegmentEvolution(String existingText, String incomingText) {
        String existingComparable = toComparableText(existingText);
        String incomingComparable = toComparableText(incomingText);
        if (existingComparable.isEmpty() || incomingComparable.isEmpty()) {
            return false;
        }
        if (existingComparable.equals(incomingComparable)
                || incomingComparable.contains(existingComparable)
                || existingComparable.contains(incomingComparable)) {
            return true;
        }
        int commonPrefix = commonPrefixLength(existingComparable, incomingComparable);
        int minLength = Math.min(existingComparable.length(), incomingComparable.length());
        return minLength > 0 && ((double) commonPrefix / minLength) >= 0.8D;
    }

    /**
     * 判断一个文本是否包含另一个文本的可比较内容。
     */
    private boolean containsComparableText(String source, String candidate) {
        String sourceComparable = toComparableText(source);
        String candidateComparable = toComparableText(candidate);
        return !sourceComparable.isEmpty() && !candidateComparable.isEmpty() && sourceComparable.contains(candidateComparable);
    }

    /**
     * 去掉标点和空白，生成比较用文本。
     */
    private String toComparableText(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * 计算两个字符串的公共前缀长度。
     */
    private int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    /**
     * 去掉右侧空白。
     */
    private String rtrim(String text) {
        if (text == null) {
            return "";
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * 判断字符串是否为空白。
     */
    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * ASR 片段内部状态。
     */
    private static final class SegmentState {
        private final int segmentId;
        private String text;
        private boolean finalized;
        private Integer bg;
        private Integer ed;
        private long updatedAt;

        /**
         * 创建片段状态。
         */
        private SegmentState(int segmentId) {
            this.segmentId = segmentId;
        }
    }
}
