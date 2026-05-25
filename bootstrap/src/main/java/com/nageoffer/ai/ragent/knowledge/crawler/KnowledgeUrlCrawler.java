package com.nageoffer.ai.ragent.knowledge.crawler;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.core.crawler.WebCrawlerGovernanceProperties;
import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlRequest;
import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlResult;
import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlerAgent;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class KnowledgeUrlCrawler {

    private final WebPageCrawlerAgent webPageCrawlerAgent;
    private final KnowledgePageParser pageParser;
    private final WebCrawlerGovernanceProperties governanceProperties;

    @Autowired
    public KnowledgeUrlCrawler(ObjectProvider<WebPageCrawlerAgent> webPageCrawlerAgent,
                               KnowledgePageParser pageParser,
                               WebCrawlerGovernanceProperties governanceProperties) {
        this(webPageCrawlerAgent == null ? null : webPageCrawlerAgent.getIfAvailable(),
                pageParser,
                governanceProperties);
    }

    public KnowledgeUrlCrawler(WebPageCrawlerAgent webPageCrawlerAgent,
                               KnowledgePageParser pageParser) {
        this(webPageCrawlerAgent, pageParser, new WebCrawlerGovernanceProperties());
    }

    private KnowledgeUrlCrawler(WebPageCrawlerAgent webPageCrawlerAgent,
                                KnowledgePageParser pageParser,
                                WebCrawlerGovernanceProperties governanceProperties) {
        this.webPageCrawlerAgent = webPageCrawlerAgent;
        this.pageParser = pageParser == null ? new KnowledgePageParser() : pageParser;
        this.governanceProperties = governanceProperties == null
                ? new WebCrawlerGovernanceProperties()
                : governanceProperties;
    }

    public boolean supports(String url, String contentType, String fileName) {
        if (!isHttpUrl(url)) {
            return false;
        }
        if (StrUtil.isNotBlank(contentType)
                && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return true;
        }
        if (StrUtil.isNotBlank(fileName)) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            return lower.endsWith(".html") || lower.endsWith(".htm");
        }
        return false;
    }

    public KnowledgeCrawledPage crawl(String url) {
        String normalizedUrl = validateUrl(url);
        if (webPageCrawlerAgent == null) {
            throw new ServiceException("Knowledge URL crawler is not available");
        }
        WebPageCrawlResult page = webPageCrawlerAgent.crawl(new WebPageCrawlRequest(
                normalizedUrl,
                governanceProperties.getMaxTextLength(),
                governanceProperties.isRespectRobotsTxt(),
                governanceProperties.getMinHostIntervalMillis()));
        return pageParser.toKnowledgePage(page);
    }

    private String validateUrl(String url) {
        if (!isHttpUrl(url)) {
            throw new ClientException("Knowledge URL must start with http:// or https://");
        }
        return url.trim();
    }

    private boolean isHttpUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
