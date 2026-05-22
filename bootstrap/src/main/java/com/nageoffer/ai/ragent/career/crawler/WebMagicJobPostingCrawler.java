package com.nageoffer.ai.ragent.career.crawler;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.crawler.webmagic", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebMagicJobPostingCrawler implements JobPostingCrawler {

    private final JobPostingPageParser parser = new JobPostingPageParser();
    private CareerCrawlerProperties properties = new CareerCrawlerProperties();

    @Autowired(required = false)
    public void setProperties(CareerCrawlerProperties properties) {
        if (properties != null) {
            this.properties = properties;
        }
    }

    @Override
    public JobPostingCrawlResult crawl(String url) {
        String normalizedUrl = validateUrl(url);
        AtomicReference<JobPostingCrawlResult> resultRef = new AtomicReference<>();
        AtomicReference<RuntimeException> failureRef = new AtomicReference<>();
        Spider.create(new PageProcessor() {
                    @Override
                    public void process(Page page) {
                        try {
                            resultRef.set(parser.parse(normalizedUrl, page.getRawText()));
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
                .addUrl(normalizedUrl)
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

    private String validateUrl(String url) {
        if (StrUtil.isBlank(url)) {
            throw new ClientException("Job URL must start with http:// or https://");
        }
        String normalized = url.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ClientException("Job URL is invalid");
        }
        String scheme = StrUtil.blankToDefault(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ClientException("Job URL must start with http:// or https://");
        }
        String host = uri.getHost();
        if (StrUtil.isBlank(host)) {
            throw new ClientException("Job URL host is required");
        }
        if (!properties.isDomainAllowed(host)) {
            throw new ClientException("Job URL domain is not in allowed domains");
        }
        validatePublicAddress(host);
        return normalized;
    }

    private void validatePublicAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new ClientException("Job URL host cannot be resolved");
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new ClientException("Cannot crawl internal network address");
            }
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isBlockedIpv4(address)
                || isBlockedIpv6(address);
    }

    private boolean isBlockedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19));
    }

    private boolean isBlockedIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        String value = address.getHostAddress().toLowerCase(Locale.ROOT);
        return value.equals("::1") || value.startsWith("fc") || value.startsWith("fd");
    }
}
