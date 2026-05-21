package com.nageoffer.ai.ragent.career.service.tts;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.tts")
public class CareerTextToSpeechProperties {

    private boolean enabled = false;

    private int chunkMaxChars = 180;

    private String voice = "default";

    private int cacheTtlSeconds = 3600;

    private String provider = "none";

    private Xunfei xunfei = new Xunfei();

    @Data
    public static class Xunfei {

        private boolean enabled = false;

        private String appId = "";

        private String apiKey = "";

        private String apiSecret = "";

        private String host = "api-dx.xf-yun.com";

        private String createPath = "/v1/private/dts_create";

        private String queryPath = "/v1/private/dts_query";

        private String language = "zh";

        private Integer speed = 50;

        private Integer volume = 50;

        private Integer pitch = 50;

        private Integer rhy = 0;

        private String audioEncoding = "lame";

        private Integer sampleRate = 16000;

        private int timeoutSeconds = 90;

        private int pollIntervalMillis = 1500;

        private int maxTextChars = 100000;
    }
}
