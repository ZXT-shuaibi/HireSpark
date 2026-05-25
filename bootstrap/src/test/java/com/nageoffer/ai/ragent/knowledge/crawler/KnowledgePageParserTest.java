package com.nageoffer.ai.ragent.knowledge.crawler;

import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgePageParserTest {

    @Test
    void convertsCrawledPageToMarkdownArtifact() {
        KnowledgePageParser parser = new KnowledgePageParser();

        KnowledgeCrawledPage page = parser.toKnowledgePage(new WebPageCrawlResult(
                "https://docs.example.com/path/getting-started.html",
                "Getting Started",
                "Install the agent and create a knowledge base.",
                List.of("https://docs.example.com/path/next"),
                "text/html"));

        assertThat(page.fileName()).isEqualTo("docs-example-com-getting-started.html.md");
        assertThat(page.contentType()).isEqualTo("text/markdown;charset=UTF-8");
        assertThat(page.markdown()).contains("# Getting Started");
        assertThat(page.markdown()).contains("Source URL: https://docs.example.com/path/getting-started.html");
        assertThat(page.markdown()).contains("Install the agent");
        assertThat(page.markdown()).contains("- https://docs.example.com/path/next");
        assertThat(page.contentHash()).hasSize(64);
        assertThat(page.size()).isEqualTo(page.content().length);
    }
}
