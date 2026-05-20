package com.nageoffer.ai.ragent.rag.core.memory;

public class ConversationMemoryTriggerContext {

    private final boolean stageSwitch;
    private final boolean recoveryEvent;

    private ConversationMemoryTriggerContext(boolean stageSwitch, boolean recoveryEvent) {
        this.stageSwitch = stageSwitch;
        this.recoveryEvent = recoveryEvent;
    }

    public static ConversationMemoryTriggerContext assistantAppend() {
        return new ConversationMemoryTriggerContext(false, false);
    }

    public static ConversationMemoryTriggerContext stageSwitch() {
        return new ConversationMemoryTriggerContext(true, false);
    }

    public static ConversationMemoryTriggerContext recoveryEvent() {
        return new ConversationMemoryTriggerContext(false, true);
    }

    public boolean isStageSwitch() {
        return stageSwitch;
    }

    public boolean isRecoveryEvent() {
        return recoveryEvent;
    }
}
