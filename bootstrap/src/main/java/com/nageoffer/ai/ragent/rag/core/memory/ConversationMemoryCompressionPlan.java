package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;

import java.util.List;

public class ConversationMemoryCompressionPlan {

    private final String traceId;
    private final List<ConversationMessageDO> hotContextMessages;
    private final List<ConversationMessageDO> shortSummaryMessages;
    private final List<ConversationMessageDO> longTermFactMessages;
    private final List<ConversationMessageDO> keyEvidenceMessages;
    private final List<ConversationMessageDO> riskFlagMessages;
    private final List<ConversationMessageDO> protectedMessages;
    private final List<String> sourceMessageIds;
    private final List<String> protectedMessageIds;
    private final List<String> sourceTurnRefs;
    private final List<String> protectedTurnRefs;

    public ConversationMemoryCompressionPlan(List<ConversationMessageDO> recentMessages,
                                             List<ConversationMessageDO> protectedMessages,
                                             List<ConversationMessageDO> summarizableMessages,
                                             List<String> sourceMessageIds,
                                             List<String> protectedMessageIds) {
        this(null, recentMessages, summarizableMessages, List.of(), protectedMessages, List.of(),
                sourceMessageIds, protectedMessageIds, List.of(), List.of());
    }

    public ConversationMemoryCompressionPlan(String traceId,
                                             List<ConversationMessageDO> hotContextMessages,
                                             List<ConversationMessageDO> shortSummaryMessages,
                                             List<ConversationMessageDO> longTermFactMessages,
                                             List<ConversationMessageDO> keyEvidenceMessages,
                                             List<ConversationMessageDO> riskFlagMessages,
                                             List<String> sourceMessageIds,
                                             List<String> protectedMessageIds,
                                             List<String> sourceTurnRefs,
                                             List<String> protectedTurnRefs) {
        this.traceId = traceId;
        this.hotContextMessages = hotContextMessages == null ? List.of() : List.copyOf(hotContextMessages);
        this.shortSummaryMessages = shortSummaryMessages == null ? List.of() : List.copyOf(shortSummaryMessages);
        this.longTermFactMessages = longTermFactMessages == null ? List.of() : List.copyOf(longTermFactMessages);
        this.keyEvidenceMessages = keyEvidenceMessages == null ? List.of() : List.copyOf(keyEvidenceMessages);
        this.riskFlagMessages = riskFlagMessages == null ? List.of() : List.copyOf(riskFlagMessages);
        this.protectedMessages = mergeProtectedMessages(this.longTermFactMessages, this.keyEvidenceMessages, this.riskFlagMessages);
        this.sourceMessageIds = sourceMessageIds == null ? List.of() : List.copyOf(sourceMessageIds);
        this.protectedMessageIds = protectedMessageIds == null ? List.of() : List.copyOf(protectedMessageIds);
        this.sourceTurnRefs = sourceTurnRefs == null ? List.of() : List.copyOf(sourceTurnRefs);
        this.protectedTurnRefs = protectedTurnRefs == null ? List.of() : List.copyOf(protectedTurnRefs);
    }

    private List<ConversationMessageDO> mergeProtectedMessages(List<ConversationMessageDO> longTermFactMessages,
                                                               List<ConversationMessageDO> keyEvidenceMessages,
                                                               List<ConversationMessageDO> riskFlagMessages) {
        List<ConversationMessageDO> merged = new java.util.ArrayList<>();
        merged.addAll(longTermFactMessages);
        merged.addAll(keyEvidenceMessages);
        merged.addAll(riskFlagMessages);
        return List.copyOf(merged);
    }

    public static ConversationMemoryCompressionPlan empty() {
        return new ConversationMemoryCompressionPlan(null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    public String getTraceId() {
        return traceId;
    }

    public List<ConversationMessageDO> getHotContextMessages() {
        return hotContextMessages;
    }

    public List<ConversationMessageDO> getShortSummaryMessages() {
        return shortSummaryMessages;
    }

    public List<ConversationMessageDO> getLongTermFactMessages() {
        return longTermFactMessages;
    }

    public List<ConversationMessageDO> getKeyEvidenceMessages() {
        return keyEvidenceMessages;
    }

    public List<ConversationMessageDO> getRiskFlagMessages() {
        return riskFlagMessages;
    }

    public List<ConversationMessageDO> getRecentMessages() {
        return hotContextMessages;
    }

    public List<ConversationMessageDO> getProtectedMessages() {
        return protectedMessages;
    }

    public List<ConversationMessageDO> getSummarizableMessages() {
        return shortSummaryMessages;
    }

    public List<String> getSourceMessageIds() {
        return sourceMessageIds;
    }

    public List<String> getProtectedMessageIds() {
        return protectedMessageIds;
    }

    public List<String> getSourceTurnRefs() {
        return sourceTurnRefs;
    }

    public List<String> getProtectedTurnRefs() {
        return protectedTurnRefs;
    }
}
