package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisProperties;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisRequest;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisProviderResult;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorAnalysisService;
import com.nageoffer.ai.ragent.career.service.demeanor.CareerDemeanorObservation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void authorizedDemeanorAnalysisUsesProviderWhenImageIsPresent() {
        CareerDemeanorAnalysisProperties properties = new CareerDemeanorAnalysisProperties();
        properties.setEnabled(true);
        AtomicReference<String> sampledAt = new AtomicReference<>();
        CareerDemeanorAnalysisService service = new CareerDemeanorAnalysisService(properties,
                request -> {
                    sampledAt.set(request.sampledAt());
                    return new CareerDemeanorAnalysisProviderResult(
                            20, 80, 75, 88, List.of("stable-eye-contact", "calm-expression"), "workflow-ok");
                });

        var result = service.analyze(new CareerDemeanorAnalysisRequest("session-1", true,
                List.of(), "https://image.example/candidate.png", "", "2026-05-21T17:00:00+08:00"));

        assertThat(result.enabled()).isTrue();
        assertThat(result.status()).isEqualTo("AUXILIARY_READY");
        assertThat(result.includedInScore()).isFalse();
        assertThat(result.confidence()).isEqualTo(0.88D);
        assertThat(result.signals()).contains("stable-eye-contact", "calm-expression", "composite-score:88");
        assertThat(sampledAt).hasValue("2026-05-21T17:00:00+08:00");
    }

    @Test
    void providerFailureFallsBackToAuxiliaryUnavailableWithoutScoring() {
        CareerDemeanorAnalysisProperties properties = new CareerDemeanorAnalysisProperties();
        properties.setEnabled(true);
        CareerDemeanorAnalysisService service = new CareerDemeanorAnalysisService(properties, request -> {
            throw new IllegalStateException("workflow down");
        });

        var result = service.analyze(new CareerDemeanorAnalysisRequest("session-1", true,
                List.of(), "https://image.example/candidate.png", ""));

        assertThat(result.enabled()).isFalse();
        assertThat(result.status()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.includedInScore()).isFalse();
        assertThat(result.signals()).contains("workflow down");
    }
}
