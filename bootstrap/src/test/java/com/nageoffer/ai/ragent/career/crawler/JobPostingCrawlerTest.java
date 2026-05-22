package com.nageoffer.ai.ragent.career.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingCrawlerTest {

    @Test
    void extractsReadableJobDescriptionFromHtml() {
        String html = """
                <html>
                  <head><title>高级 Java 工程师 - 星河科技</title></head>
                  <body>
                    <nav>首页 登录 注册</nav>
                    <section class="job-detail">
                      <h1>高级 Java 工程师</h1>
                      <p>岗位职责：负责 Spring Boot 后端服务、RAG 应用和 Agent 平台建设。</p>
                      <p>任职要求：熟悉 PostgreSQL、Redis、消息队列和工程质量治理。</p>
                    </section>
                    <script>window.tracker=true</script>
                  </body>
                </html>
                """;

        JobPostingPageParser parser = new JobPostingPageParser();

        JobPostingCrawlResult result = parser.parse("https://jobs.example/java", html);

        assertThat(result.title()).contains("高级 Java 工程师");
        assertThat(result.rawText()).contains("岗位职责");
        assertThat(result.rawText()).contains("Spring Boot");
        assertThat(result.rawText()).doesNotContain("window.tracker");
    }
}
