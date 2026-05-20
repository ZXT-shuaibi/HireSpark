package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisProperties;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisRequest;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisService;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorObservation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CareerDemeanorAnalysisServiceTest {

    @Test
    void disabledOrUnauthorizedDemeanorAnalysisReturnsAuxiliaryDisabledResult() {
        CareerDemeanorAnalysisProperties properties = new CareerDemeanorAnalysisProperties();
        properties.setEnabled(false);
        CareerDemeanorAnalysisService service = new CareerDemeanorAnalysisService(properties);

        var result = service.analyze(new CareerDemeanorAnalysisRequest("session-1", false, List.of()));

        assertThat(result.enabled()).isFalse();
        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.includedInScore()).isFalse();
        assertThat(result.limitations()).contains("auxiliary-signal-only");
    }

    @Test
    void authorizedDemeanorAnalysisNormalizesConfidenceAndKeepsLimitations() {
        CareerDemeanorAnalysisProperties properties = new CareerDemeanorAnalysisProperties();
        properties.setEnabled(true);
        CareerDemeanorAnalysisService service = new CareerDemeanorAnalysisService(properties);

        var result = service.analyze(new CareerDemeanorAnalysisRequest("session-1", true, List.of(
                new CareerDemeanorObservation("eye-contact", 0.8D, "candidate looked at camera"),
                new CareerDemeanorObservation("unstable-audio", 0.4D, "audio delay may affect judgment")
        )));

        assertThat(result.enabled()).isTrue();
        assertThat(result.status()).isEqualTo("AUXILIARY_READY");
        assertThat(result.includedInScore()).isFalse();
        assertThat(result.confidence()).isEqualTo(0.6D);
        assertThat(result.signals()).contains("eye-contact", "unstable-audio");
        assertThat(result.retentionPolicy()).isEqualTo("derived-summary-only");
        assertThat(result.limitations()).contains("not-a-hiring-decision", "requires-user-consent");
    }
}
