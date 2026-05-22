package com.nageoffer.ai.ragent.core.crawler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "core.crawler.webmagic")
public class WebCrawlerGovernanceProperties {

    private boolean enabled = true;

    private boolean respectRobotsTxt = true;

    private int retryTimes = 1;

    private int timeoutMillis = 8000;

    private int sleepMillis = 200;

    private int maxTextLength = 50000;

    private long minHostIntervalMillis = 500L;

    private String userAgent = "Mozilla/5.0 Ragent WebMagic Knowledge Crawler";
}
