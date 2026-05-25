package com.nageoffer.ai.ragent.knowledge.crawler;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.core.crawler.WebPageCrawlResult;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

@Component
public class KnowledgePageParser {

    public KnowledgeCrawledPage toKnowledgePage(WebPageCrawlResult page) {
        if (page == null || StrUtil.isBlank(page.text())) {
            throw new ServiceException("Crawled web page content is empty");
        }
        String markdown = toMarkdown(page);
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
        return new KnowledgeCrawledPage(
                page.url(),
                page.title(),
                markdown,
                content,
                content.length,
                fileName(page),
                KnowledgeCrawledPage.MARKDOWN_CONTENT_TYPE,
                sha256Hex(content));
    }

    private String toMarkdown(WebPageCrawlResult page) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(firstHasText(page.title(), "Untitled Web Page")).append("\n\n");
        builder.append("Source URL: ").append(page.url()).append("\n\n");
        builder.append(page.text()).append("\n");
        List<String> links = page.links() == null ? List.of() : page.links();
        if (!links.isEmpty()) {
            builder.append("\n## Links\n");
            links.stream().limit(50).forEach(link -> builder.append("- ").append(link).append("\n"));
        }
        return builder.toString();
    }

    private String fileName(WebPageCrawlResult page) {
        String host = "web-page";
        String slug = null;
        try {
            URI uri = URI.create(StrUtil.blankToDefault(page.url(), ""));
            if (StrUtil.isNotBlank(uri.getHost())) {
                host = uri.getHost().replace('.', '-');
            }
            String path = uri.getPath();
            if (StrUtil.isNotBlank(path)) {
                String[] parts = path.split("/");
                slug = parts.length == 0 ? null : parts[parts.length - 1];
            }
        } catch (Exception ignored) {
            slug = page.title();
        }
        String token = safeToken(host + "-" + firstHasText(slug, page.title(), "page"));
        return token + ".md";
    }

    private String safeToken(String value) {
        String token = firstHasText(value, "web-page").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StrUtil.blankToDefault(token, "web-page");
    }

    private String firstHasText(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hexEncode(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new ServiceException("SHA-256 algorithm is not available");
        }
    }

    private String hexEncode(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String value = Integer.toHexString(0xff & b);
            if (value.length() == 1) {
                hex.append('0');
            }
            hex.append(value);
        }
        return hex.toString();
    }
}
