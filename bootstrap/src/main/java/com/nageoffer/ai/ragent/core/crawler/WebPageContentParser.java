package com.nageoffer.ai.ragent.core.crawler;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class WebPageContentParser {

    private final int maxTextLength;

    public WebPageContentParser(int maxTextLength) {
        this.maxTextLength = Math.max(1, maxTextLength);
    }

    public WebPageCrawlResult parse(String url, String html) {
        Document document = Jsoup.parse(StrUtil.blankToDefault(html, ""), url);
        document.select("script,style,noscript,svg,canvas,iframe,header,footer,nav").remove();
        String title = firstNotBlank(
                document.select("article h1").stream().map(element -> element.text()).findFirst().orElse(null),
                document.select("h1").stream().map(element -> element.text()).findFirst().orElse(null),
                document.title());
        String text = normalize(document.body() == null ? document.text() : document.body().text());
        if (text.length() > maxTextLength) {
            text = text.substring(0, maxTextLength);
        }
        List<String> links = document.select("a[href]").stream()
                .map(element -> element.absUrl("href"))
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(100)
                .toList();
        return new WebPageCrawlResult(url, title, text, links, "text/html");
    }

    private String normalize(String value) {
        return StrUtil.blankToDefault(value, "")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
