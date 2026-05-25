package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.dao.entity.CareerDecisionIndexDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerDecisionIndexMapper;
import com.nageoffer.ai.ragent.career.service.agent.BusinessAgentScene;
import com.nageoffer.ai.ragent.career.service.decision.CareerDecisionIndexCommand;
import com.nageoffer.ai.ragent.career.service.decision.CareerDecisionIndexService;
import com.nageoffer.ai.ragent.career.service.decision.CareerDecisionIndexServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CareerDecisionIndexServiceTest {

    private final CareerDecisionIndexMapper mapper = mock(CareerDecisionIndexMapper.class);
    private final CareerDecisionIndexService service = new CareerDecisionIndexServiceImpl(mapper);

    @Test
    void recordsCrossAgentDecisionWithSanitizedReferences() {
        service.record(CareerDecisionIndexCommand.builder()
                .traceId("trace-1")
                .userId("user-1")
                .businessScene(BusinessAgentScene.INTERVIEW.name())
                .businessId("session-1")
                .agentType("INTERVIEW_EVALUATOR")
                .decisionType("INTERVIEW_EVALUATION")
                .decisionKey("session-1:turn-2")
                .decisionSummary("score=72, followUp=true")
                .inputRef(Map.of("turnNo", 2, "answerChars", 88))
                .outputRef(Map.of("score", 72, "matchedRule", "MISSING_POINTS"))
                .build());

        ArgumentCaptor<CareerDecisionIndexDO> captor = ArgumentCaptor.forClass(CareerDecisionIndexDO.class);
        verify(mapper).insert(captor.capture());
        CareerDecisionIndexDO record = captor.getValue();
        assertThat(record.getTraceId()).isEqualTo("trace-1");
        assertThat(record.getBusinessScene()).isEqualTo("INTERVIEW");
        assertThat(record.getAgentType()).isEqualTo("INTERVIEW_EVALUATOR");
        assertThat(record.getDecisionSummary()).isEqualTo("score=72, followUp=true");
        assertThat(record.getInputRefJson()).contains("answerChars").doesNotContain("完整答案");
        assertThat(record.getOutputRefJson()).contains("MISSING_POINTS");
    }
}
