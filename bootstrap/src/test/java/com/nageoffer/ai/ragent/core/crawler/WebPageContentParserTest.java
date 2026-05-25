package com.nageoffer.ai.ragent.core.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebPageContentParserTest {

    @Test
    void extractsReadableTextAndAbsoluteLinksFromHtml() {
        String html = """
                <html>
                  <head><title>RAG Knowledge</title></head>
                  <body>
                    <nav>Login Register</nav>
                    <article>
                      <h1>Knowledge URL Import</h1>
                      <p>Use WebMagic to crawl pages into the knowledge base.</p>
                      <a href="/docs/rag">RAG docs</a>
                    </article>
                    <script>window.tracker=true</script>
                  </body>
                </html>
                """;

        WebPageCrawlResult result = new WebPageContentParser(200)
                .parse("https://kb.example/import", html);

        assertThat(result.title()).isEqualTo("Knowledge URL Import");
        assertThat(result.text()).contains("Use WebMagic to crawl pages into the knowledge base.");
        assertThat(result.text()).doesNotContain("window.tracker");
        assertThat(result.links()).contains("https://kb.example/docs/rag");
    }
}
