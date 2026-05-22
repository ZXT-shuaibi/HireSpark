package com.nageoffer.ai.ragent.career.crawler;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.crawler")
public class CareerCrawlerProperties {

    private List<String> allowedDomains = List.of();

    private JdVerification jdVerification = new JdVerification();

    public boolean isDomainAllowed(String host) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }
        String normalizedHost = normalizeDomain(host);
        if (StrUtil.isBlank(normalizedHost)) {
            return false;
        }
        return allowedDomains.stream()
                .map(this::normalizeDomain)
                .filter(StrUtil::isNotBlank)
                .anyMatch(domain -> normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
    }

    private String normalizeDomain(String value) {
        String domain = StrUtil.blankToDefault(value, "").trim().toLowerCase(Locale.ROOT);
        while (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        while (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain;
    }

    @Data
    public static class JdVerification {

        private boolean ruleEnabled = true;

        private boolean llmEnabled = false;

        private int minTextLength = 100;

        private int minSignalMatches = 2;

        private List<String> signalWords = List.of(
                "\u5c97\u4f4d", "\u804c\u8d23", "\u8981\u6c42", "\u4efb\u804c",
                "\u85aa\u8d44", "\u7ecf\u9a8c", "\u5b66\u5386", "\u62db\u8058",
                "\u804c\u4f4d", "\u5de5\u4f5c\u5185\u5bb9", "\u4efb\u804c\u8d44\u683c",
                "\u5c97\u4f4d\u804c\u8d23", "\u5c97\u4f4d\u8981\u6c42",
                "responsibilities", "requirements", "qualifications",
                "experience", "education", "salary", "job description");
    }
}
