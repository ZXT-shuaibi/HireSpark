package com.nageoffer.ai.ragent.core.crawler;

import java.util.List;

public record WebPageCrawlResult(String url,
                                 String title,
                                 String text,
                                 List<String> links,
                                 String contentType) {
}
