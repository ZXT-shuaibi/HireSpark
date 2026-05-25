package com.nageoffer.ai.ragent.core.crawler;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "core.crawler.webmagic", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebMagicWebPageCrawlerAgent implements WebPageCrawlerAgent {

    private final WebCrawlerGovernanceProperties properties;
    private final WebCrawlerHostRateLimiter rateLimiter;

    @Override
    public WebPageCrawlResult crawl(WebPageCrawlRequest request) {
        String url = validateUrl(request == null ? null : request.url());
        int maxTextLength = request != null && request.maxTextLength() > 0
                ? request.maxTextLength()
                : properties.getMaxTextLength();
        long rateInterval = request != null && request.minHostIntervalMillis() > 0
                ? request.minHostIntervalMillis()
                : properties.getMinHostIntervalMillis();
        boolean robots = (request != null && request.respectRobotsTxt()) || properties.isRespectRobotsTxt();
        rateLimiter.awaitTurn(url, rateInterval);
        AtomicReference<WebPageCrawlResult> resultRef = new AtomicReference<>();
        AtomicReference<RuntimeException> failureRef = new AtomicReference<>();
        WebPageContentParser parser = new WebPageContentParser(maxTextLength);
        Spider.create(new PageProcessor() {
                    @Override
                    public void process(Page page) {
                        try {
                            resultRef.set(parser.parse(url, page.getRawText()));
                        } catch (RuntimeException ex) {
                            failureRef.set(ex);
                        }
                    }

                    @Override
                    public Site getSite() {
                        return Site.me()
                                .setRetryTimes(properties.getRetryTimes())
                                .setSleepTime(properties.getSleepMillis())
                                .setTimeOut(properties.getTimeoutMillis())
                                .setUserAgent(properties.getUserAgent())
                                .addHeader("X-Ragent-Crawler-Policy", robots ? "robots-aware" : "robots-disabled")
                                .addHeader("X-Ragent-Crawler-Use", "knowledge-url-import");
                    }
                })
                .addUrl(url)
                .thread(1)
                .run();
        if (failureRef.get() != null) {
            throw failureRef.get();
        }
        WebPageCrawlResult result = resultRef.get();
        if (result == null || StrUtil.isBlank(result.text())) {
            throw new ServiceException("Failed to crawl readable web page content");
        }
        return result;
    }

    private String validateUrl(String url) {
        if (StrUtil.isBlank(url)) {
            throw new ClientException("Web page URL is required");
        }
        String normalized = url.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ClientException("Web page URL must start with http:// or https://");
        }
        return normalized;
    }
}
