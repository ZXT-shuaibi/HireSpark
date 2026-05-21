package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.agent.BusinessAgentScene;
import com.nageoffer.ai.ragent.career.service.agent.CareerAgentDescriptor;
import com.nageoffer.ai.ragent.career.service.agent.CareerAgentResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerAgentResolverTest {

    private final CareerAgentResolver resolver = new CareerAgentResolver();

    @Test
    void resolvesInterviewEvaluationSceneFromSingleFlightContext() {
        CareerAgentDescriptor descriptor = resolver.resolve(
                "INTERVIEW_EVALUATE",
                "INTERVIEW_EVALUATE:user-1:session-9:turn-2");

        assertThat(descriptor.agentType()).isEqualTo("INTERVIEW_EVALUATOR");
        assertThat(descriptor.businessScene()).isEqualTo(BusinessAgentScene.INTERVIEW);
        assertThat(descriptor.userId()).isEqualTo("user-1");
        assertThat(descriptor.businessId()).isEqualTo("session-9");
        assertThat(descriptor.decisionType()).isEqualTo("INTERVIEW_EVALUATION");
    }

    @Test
    void resolvesOptimizationExecutorScene() {
        CareerAgentDescriptor descriptor = resolver.resolve(
                "OPTIMIZATION_EXECUTOR",
                "OPTIMIZATION_EXECUTOR:user-2:task-3:prompt");

        assertThat(descriptor.agentType()).isEqualTo("RESUME_OPTIMIZATION_EXECUTOR");
        assertThat(descriptor.businessScene()).isEqualTo(BusinessAgentScene.OPTIMIZATION);
        assertThat(descriptor.userId()).isEqualTo("user-2");
        assertThat(descriptor.businessId()).isEqualTo("task-3");
        assertThat(descriptor.decisionType()).isEqualTo("RESUME_OPTIMIZATION");
    }
}
