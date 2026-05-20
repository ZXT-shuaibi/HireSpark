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
}
