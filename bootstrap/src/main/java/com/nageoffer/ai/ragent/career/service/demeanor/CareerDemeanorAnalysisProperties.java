package com.nageoffer.ai.ragent.career.service.demeanor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.demeanor")
public class CareerDemeanorAnalysisProperties {

    private boolean enabled = false;

    private String retentionPolicy = "derived-summary-only";

    private List<String> limitations = new ArrayList<>(List.of(
            "auxiliary-signal-only",
            "not-a-hiring-decision",
            "requires-user-consent"
    ));
}
