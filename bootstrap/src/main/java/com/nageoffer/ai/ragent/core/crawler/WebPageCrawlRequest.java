package com.nageoffer.ai.ragent.core.crawler;

public record WebPageCrawlRequest(String url,
                                  int maxTextLength,
                                  boolean respectRobotsTxt,
                                  long minHostIntervalMillis) {
}
