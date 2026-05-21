package com.nageoffer.ai.ragent.career.service.agent;

public record CareerAgentDescriptor(String agentType,
                                    BusinessAgentScene businessScene,
                                    String userId,
                                    String businessId,
                                    String decisionType) {
}
