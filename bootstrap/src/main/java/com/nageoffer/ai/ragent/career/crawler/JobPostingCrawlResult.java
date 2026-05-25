package com.nageoffer.ai.ragent.career.crawler;

public record JobPostingCrawlResult(String url,
                                    String title,
                                    String company,
                                    String rawText) {
}
