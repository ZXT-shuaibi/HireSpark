package com.nageoffer.ai.ragent.career.crawler;

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

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.crawler.webmagic", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebMagicJobPostingCrawler implements JobPostingCrawler {

    private final JobPostingPageParser parser = new JobPostingPageParser();

    @Override
    public JobPostingCrawlResult crawl(String url) {
        if (!isHttpUrl(url)) {
            throw new ClientException("Job URL must start with http:// or https://");
        }
        AtomicReference<JobPostingCrawlResult> resultRef = new AtomicReference<>();
        AtomicReference<RuntimeException> failureRef = new AtomicReference<>();
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
                                .setRetryTimes(1)
                                .setSleepTime(100)
                                .setTimeOut(8000)
                                .setUserAgent("Mozilla/5.0 Ragent Career JD Crawler");
                    }
                })
                .addUrl(url)
                .thread(1)
                .run();
        if (failureRef.get() != null) {
            throw failureRef.get();
        }
        JobPostingCrawlResult result = resultRef.get();
        if (result == null || StrUtil.isBlank(result.rawText())) {
            throw new ServiceException("Failed to crawl job description from URL");
        }
        return result;
    }

    private boolean isHttpUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        String normalized = url.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }
}
