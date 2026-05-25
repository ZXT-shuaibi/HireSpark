package com.nageoffer.ai.ragent.career.crawler;

import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class JobPostingPageParser {

    private static final int MAX_TEXT_LENGTH = 20000;

    public JobPostingCrawlResult parse(String url, String html) {
        Document document = Jsoup.parse(StrUtil.blankToDefault(html, ""), url);
        document.select("script,style,noscript,svg,canvas,iframe,header,footer,nav").remove();
        String title = firstNotBlank(
                document.select("h1").stream().map(element -> element.text()).findFirst().orElse(null),
                document.title());
        String rawText = normalize(document.body() == null ? document.text() : document.body().text());
        if (rawText.length() > MAX_TEXT_LENGTH) {
            rawText = rawText.substring(0, MAX_TEXT_LENGTH);
        }
        return new JobPostingCrawlResult(url, title, null, rawText);
    }

    private String normalize(String value) {
        return StrUtil.blankToDefault(value, "")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstNotBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first.trim();
        }
        return StrUtil.isBlank(second) ? null : second.trim();
    }
}
