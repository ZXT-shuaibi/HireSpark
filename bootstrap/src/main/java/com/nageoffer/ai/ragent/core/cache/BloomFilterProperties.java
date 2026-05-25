package com.nageoffer.ai.ragent.core.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ragent.cache.bloom")
public class BloomFilterProperties {

    private Boolean enabled = false;

    private String name = "ragent:bloom:default";

    private Long expectedInsertions = 1_000_000L;

    private Double falseProbability = 0.01D;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public long safeExpectedInsertions() {
        return expectedInsertions == null || expectedInsertions <= 0 ? 1_000_000L : expectedInsertions;
    }

    public double safeFalseProbability() {
        if (falseProbability == null || falseProbability <= 0D || falseProbability >= 1D) {
            return 0.01D;
        }
        return falseProbability;
    }
}
