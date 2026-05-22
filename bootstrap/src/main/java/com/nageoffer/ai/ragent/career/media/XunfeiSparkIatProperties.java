package com.nageoffer.ai.ragent.career.media;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.voice.xunfei-iat")
public class XunfeiSparkIatProperties {

    private Boolean enabled = false;

    private String appId;

    private String apiKey;

    private String apiSecret;

    private String host = "spark-api.xf-yun.com";

    private String endpointBaseUrl;

    private String transcribePath = "/v2/iat";

    private String domain = "iat";

    private String language = "zh_cn";

    private String accent = "mandarin";

    private String audioEncoding = "raw";

    private String audioFormat = "audio/L16;rate=16000";

    private Integer timeoutSeconds = 60;

    private Integer maxAudioBytes = 10 * 1024 * 1024;

    public int effectiveTimeoutSeconds() {
        return timeoutSeconds == null || timeoutSeconds <= 0 ? 60 : Math.min(timeoutSeconds, 300);
    }

    public int effectiveMaxAudioBytes() {
        return maxAudioBytes == null || maxAudioBytes <= 0 ? 10 * 1024 * 1024 : maxAudioBytes;
    }
}
