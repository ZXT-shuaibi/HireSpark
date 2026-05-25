package com.nageoffer.ai.ragent.core.crawler;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebCrawlerHostRateLimiter {

    private final Map<String, Long> lastAccessByHost = new ConcurrentHashMap<>();

    public void awaitTurn(String url, long minIntervalMillis) {
        if (minIntervalMillis <= 0) {
            return;
        }
        String host = host(url);
        if (host == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAccessByHost.put(host, now);
        if (last == null) {
            return;
        }
        long waitMillis = minIntervalMillis - (now - last);
        if (waitMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ex) {
            return null;
        }
    }
}
