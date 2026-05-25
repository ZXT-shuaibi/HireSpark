package com.nageoffer.ai.ragent.career.service.xunfei;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.xunfei")
public class XunfeiVisionNlpProperties {

    private HttpFeature ocr = new HttpFeature();

    private HttpFeature faceDetect = new HttpFeature();

    private HttpFeature nlp = new HttpFeature();

    @Data
    public static class HttpFeature {

        private boolean enabled = false;

        private String appId = "";

        private String apiKey = "";

        private String apiSecret = "";

        private String host = "api.xf-yun.com";

        private String endpointBaseUrl = "";

        private String path = "/";

        private String service = "general";

        private int timeoutSeconds = 30;

        private int maxImageBytes = 5 * 1024 * 1024;

        private int maxTextChars = 20000;

        public int effectiveTimeoutSeconds() {
            return timeoutSeconds <= 0 ? 30 : Math.min(timeoutSeconds, 300);
        }

        public int effectiveMaxImageBytes() {
            return maxImageBytes <= 0 ? 5 * 1024 * 1024 : maxImageBytes;
        }

        public int effectiveMaxTextChars() {
            return maxTextChars <= 0 ? 20000 : maxTextChars;
        }
    }
}
