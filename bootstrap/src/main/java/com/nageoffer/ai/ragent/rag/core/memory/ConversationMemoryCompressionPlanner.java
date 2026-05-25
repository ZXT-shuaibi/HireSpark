package com.nageoffer.ai.ragent.rag.core.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationMemoryCompressionPlanner {

    private final ConversationMemoryImportanceScorer importanceScorer;

    public ConversationMemoryCompressionPlan plan(List<ConversationMessageDO> messages, MemoryProperties properties) {
        return plan(messages, properties, null);
    }

    public ConversationMemoryCompressionPlan plan(List<ConversationMessageDO> messages,
                                                  MemoryProperties properties,
                                                  String traceId) {
        if (CollUtil.isEmpty(messages)) {
            return ConversationMemoryCompressionPlan.empty();
        }
        MemoryProperties safeProperties = properties == null ? new MemoryProperties() : properties;
        List<ConversationMessageDO> eligible = messages.stream()
                .filter(this::eligible)
                .toList();
        if (eligible.isEmpty()) {
            return ConversationMemoryCompressionPlan.empty();
        }

        int recentKeepMessages = Math.max(0, valueOrDefault(safeProperties.getRecentKeepMessages(), 6));
        int recentStart = Math.max(0, eligible.size() - recentKeepMessages);
        List<ConversationMessageDO> olderMessages = new ArrayList<>(eligible.subList(0, recentStart));
        List<ConversationMessageDO> hotContextMessages = new ArrayList<>(eligible.subList(recentStart, eligible.size()));

        int importantThreshold = valueOrDefault(safeProperties.getImportantMessageThreshold(), 70);
        List<ConversationMessageDO> shortSummaryMessages = new ArrayList<>();
        List<ConversationMessageDO> longTermFactMessages = new ArrayList<>();
        List<ConversationMessageDO> keyEvidenceMessages = new ArrayList<>();
        List<ConversationMessageDO> riskFlagMessages = new ArrayList<>();
        for (ConversationMessageDO message : olderMessages) {
            if (importanceScorer.score(message) >= importantThreshold || importanceScorer.hasProtectedSignal(message)) {
                ConversationMemoryBucket bucket = classifyProtectedBucket(message);
                if (bucket == ConversationMemoryBucket.RISK_FLAG) {
                    riskFlagMessages.add(message);
                } else if (bucket == ConversationMemoryBucket.KEY_EVIDENCE) {
                    keyEvidenceMessages.add(message);
                } else {
                    longTermFactMessages.add(message);
                }
            } else {
                shortSummaryMessages.add(message);
            }
        }

        shortSummaryMessages = limitToNewest(
                shortSummaryMessages,
                valueOrDefault(safeProperties.getSummaryMaxSourceMessages(), 30)
        );
        List<ConversationMessageDO> protectedMessages = new ArrayList<>();
        protectedMessages.addAll(longTermFactMessages);
        protectedMessages.addAll(keyEvidenceMessages);
        protectedMessages.addAll(riskFlagMessages);
        return new ConversationMemoryCompressionPlan(
                traceId,
                hotContextMessages,
                shortSummaryMessages,
                longTermFactMessages,
                keyEvidenceMessages,
                riskFlagMessages,
                ids(shortSummaryMessages),
                ids(protectedMessages),
                turnRefs(eligible, shortSummaryMessages),
                turnRefs(eligible, protectedMessages)
        );
    }

    private boolean eligible(ConversationMessageDO message) {
        return message != null
                && StrUtil.isNotBlank(message.getId())
                && StrUtil.isNotBlank(message.getRole())
                && StrUtil.isNotBlank(message.getContent());
    }

    private List<ConversationMessageDO> limitToNewest(List<ConversationMessageDO> messages, int maxMessages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        int safeMax = Math.max(1, maxMessages);
        if (messages.size() <= safeMax) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - safeMax, messages.size()));
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private ConversationMemoryBucket classifyProtectedBucket(ConversationMessageDO message) {
        if (importanceScorer.hasRiskSignal(message)) {
            return ConversationMemoryBucket.RISK_FLAG;
        }
        if (importanceScorer.hasKeyEvidenceSignal(message)) {
            return ConversationMemoryBucket.KEY_EVIDENCE;
        }
        return ConversationMemoryBucket.LONG_TERM_FACT;
    }

    private List<String> ids(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream()
                .map(ConversationMessageDO::getId)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private List<String> turnRefs(List<ConversationMessageDO> allMessages, List<ConversationMessageDO> selectedMessages) {
        if (CollUtil.isEmpty(allMessages) || CollUtil.isEmpty(selectedMessages)) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        for (ConversationMessageDO selected : selectedMessages) {
            int index = allMessages.indexOf(selected);
            if (index >= 0 && StrUtil.isNotBlank(selected.getId())) {
                refs.add("turn#" + (index + 1) + ":" + selected.getId());
            }
        }
        return refs;
    }
}
