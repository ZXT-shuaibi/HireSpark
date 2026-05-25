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

    private String provider = "none";

    private String retentionPolicy = "derived-summary-only";

    private List<String> limitations = new ArrayList<>(List.of(
            "auxiliary-signal-only",
            "not-a-hiring-decision",
            "requires-user-consent"
    ));

    private XingChen xingchen = new XingChen();

    @Data
    public static class XingChen {

        private boolean enabled = false;

        private String apiKey = "";

        private String apiSecret = "";

        private String flowId = "";

        private String chatUrl = "https://xingchen-api.xf-yun.com/workflow/v1/chat/completions";

        private String uploadUrl = "https://xingchen-api.xf-yun.com/workflow/v1/upload_file";

        private int timeoutSeconds = 60;

        private int maxImageBytes = 5 * 1024 * 1024;
    }
}
