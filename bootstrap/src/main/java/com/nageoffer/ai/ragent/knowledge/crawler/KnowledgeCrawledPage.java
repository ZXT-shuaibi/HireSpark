package com.nageoffer.ai.ragent.knowledge.crawler;

public record KnowledgeCrawledPage(String url,
                                   String title,
                                   String markdown,
                                   byte[] content,
                                   long size,
                                   String fileName,
                                   String contentType,
                                   String contentHash) {

    public static final String MARKDOWN_CONTENT_TYPE = "text/markdown;charset=UTF-8";
}
