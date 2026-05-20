package com.nageoffer.ai.ragent.user.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenConfigTest {

    @Test
    void publicPathPatternsIncludeSpringdocAssets() {
        assertThat(SaTokenConfig.publicPathPatterns())
                .contains("/auth/**", "/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/v3/api-docs.yaml", "/webjars/**");
    }

    @Test
    void publicPathPatternsIncludeActuatorHealthProbes() {
        assertThat(SaTokenConfig.publicPathPatterns())
                .contains("/actuator/health", "/actuator/health/**");
    }
}
