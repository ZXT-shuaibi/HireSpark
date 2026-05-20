package com.nageoffer.ai.ragent.rag.core.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationMemoryTriggerPolicy {

    public ConversationMemoryTriggerDecision decide(long userTurns,
                                                    List<ConversationMessageDO> candidateMessages,
                                                    MemoryProperties properties,
                                                    ConversationMemoryTriggerContext context) {
        MemoryProperties safeProperties = properties == null ? new MemoryProperties() : properties;
        ConversationMemoryTriggerContext safeContext = context == null
                ? ConversationMemoryTriggerContext.assistantAppend()
                : context;
        int estimatedTokens = estimateTokens(candidateMessages);
        List<ConversationMemoryTrigger> triggers = new ArrayList<>();

        Integer summaryStartTurns = safeProperties.getSummaryStartTurns();
        if (summaryStartTurns != null && summaryStartTurns > 0 && userTurns >= summaryStartTurns) {
            triggers.add(ConversationMemoryTrigger.TURN_COUNT);
        }
        Integer tokenTriggerThreshold = safeProperties.getTokenTriggerThreshold();
        if (tokenTriggerThreshold != null && tokenTriggerThreshold > 0 && estimatedTokens >= tokenTriggerThreshold) {
            triggers.add(ConversationMemoryTrigger.TOKEN_ESTIMATE);
        }
        if (Boolean.TRUE.equals(safeProperties.getStageSwitchTriggerEnabled()) && safeContext.isStageSwitch()) {
            triggers.add(ConversationMemoryTrigger.STAGE_SWITCH);
        }
        if (Boolean.TRUE.equals(safeProperties.getRecoveryTriggerEnabled()) && safeContext.isRecoveryEvent()) {
            triggers.add(ConversationMemoryTrigger.RECOVERY_EVENT);
        }
        return new ConversationMemoryTriggerDecision(triggers, userTurns, estimatedTokens);
    }

    private int estimateTokens(List<ConversationMessageDO> messages) {
        if (CollUtil.isEmpty(messages)) {
            return 0;
        }
        int chars = messages.stream()
                .filter(message -> message != null && StrUtil.isNotBlank(message.getContent()))
                .mapToInt(message -> message.getContent().length())
                .sum();
        return Math.max(1, (int) Math.ceil(chars / 4.0D));
    }
}
