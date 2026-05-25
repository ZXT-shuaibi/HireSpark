package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryTriggerPolicyTest {

    private final ConversationMemoryTriggerPolicy triggerPolicy = new ConversationMemoryTriggerPolicy();

    @Test
    void resolvesTurnTokenStageAndRecoveryTriggers() {
        MemoryProperties properties = new MemoryProperties();
        properties.setSummaryStartTurns(3);
        properties.setTokenTriggerThreshold(1);
        properties.setStageSwitchTriggerEnabled(true);
        properties.setRecoveryTriggerEnabled(true);

        ConversationMemoryTriggerDecision stageDecision = triggerPolicy.decide(
                3,
                List.of(message("1", "user", "long enough content")),
                properties,
                ConversationMemoryTriggerContext.stageSwitch()
        );
        ConversationMemoryTriggerDecision recoveryDecision = triggerPolicy.decide(
                1,
                List.of(message("2", "assistant", "long enough content")),
                properties,
                ConversationMemoryTriggerContext.recoveryEvent()
        );

        assertThat(stageDecision.getTriggers()).contains(
                ConversationMemoryTrigger.TURN_COUNT,
                ConversationMemoryTrigger.TOKEN_ESTIMATE,
                ConversationMemoryTrigger.STAGE_SWITCH
        );
        assertThat(recoveryDecision.getTriggers()).contains(
                ConversationMemoryTrigger.TOKEN_ESTIMATE,
                ConversationMemoryTrigger.RECOVERY_EVENT
        );
    }

    private ConversationMessageDO message(String id, String role, String content) {
        return ConversationMessageDO.builder()
                .id(id)
                .role(role)
                .content(content)
                .build();
    }
}
