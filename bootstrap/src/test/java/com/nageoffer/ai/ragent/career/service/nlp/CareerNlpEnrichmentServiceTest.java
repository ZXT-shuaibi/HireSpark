package com.nageoffer.ai.ragent.career.service.nlp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CareerNlpEnrichmentServiceTest {

    @Test
    void enrichesTextWithProviderKeywordsEntitiesAndSentiment() {
        CareerNlpEnrichmentService service = new CareerNlpEnrichmentService(
                request -> new CareerNlpAnalysisResult("xunfei-nlp", "sid-1",
                        List.of("Spring Boot", "RAG"), List.of("PostgreSQL"), "neutral"));

        var result = service.enrich("JD_PARSE",
                "负责 Spring Boot、RAG 平台和 PostgreSQL 数据治理。", "trace-jd-1");

        assertThat(result).containsEntry("status", "READY");
        assertThat(result).containsEntry("scene", "JD_PARSE");
        assertThat(result).containsEntry("traceId", "trace-jd-1");
        assertThat(result).containsEntry("provider", "xunfei-nlp");
        assertThat(result.get("keywords")).asList().contains("Spring Boot", "RAG");
        assertThat(result.get("entities")).asList().contains("PostgreSQL");
        assertThat(result).containsEntry("sentiment", "neutral");
    }

    @Test
    void returnsEmptyPayloadWhenProviderIsNotConfigured() {
        CareerNlpEnrichmentService service = new CareerNlpEnrichmentService((CareerNlpProvider) null);

        assertThat(service.enrich("RESUME_PARSE", "Java developer", "trace-resume-1")).isEmpty();
    }
}
