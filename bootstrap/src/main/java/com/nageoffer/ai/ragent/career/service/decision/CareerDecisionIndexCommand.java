package com.nageoffer.ai.ragent.career.service.decision;

import lombok.Builder;

@Builder
public record CareerDecisionIndexCommand(String traceId,
                                         String userId,
                                         String businessScene,
                                         String businessId,
                                         String agentType,
                                         String decisionType,
                                         String decisionKey,
                                         String decisionSummary,
                                         Object inputRef,
                                         Object outputRef) {
}
