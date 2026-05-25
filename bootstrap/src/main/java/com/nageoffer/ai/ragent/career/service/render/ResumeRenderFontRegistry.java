package com.nageoffer.ai.ragent.career.service.render;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ResumeRenderFontRegistry {

    private static final String FILE_PREFIX = "file:";
    private static final String CLASSPATH_PREFIX = "classpath:";

    private final ResumeRenderFontProperties properties;
    private final ResourceLoader resourceLoader;

    public ResumeRenderFontRegistry() {
        this(new ResumeRenderFontProperties(), new DefaultResourceLoader());
    }

    @Autowired
    public ResumeRenderFontRegistry(ResumeRenderFontProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties == null ? new ResumeRenderFontProperties() : properties;
        this.resourceLoader = resourceLoader == null ? new DefaultResourceLoader() : resourceLoader;
    }

    public String cssFontFamily() {
        return properties.effectiveCssFamilies().stream()
                .map(this::formatCssFamily)
                .collect(Collectors.joining(", "));
    }

    public String pdfFontFamily() {
        return properties.effectivePdfFamily();
    }

    public List<String> fontResourceLocations() {
        return properties.effectiveFontPaths();
    }

    public Map<String, Object> fontStrategy() {
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("policy", "configured-cjk-font-stack");
        strategy.put("source", "operator-provided-or-system-installed-fonts");
        strategy.put("license", "operator-must-provide-licensed-font-files");
        strategy.put("loading", "classpath-or-file-resource-registration");
        strategy.put("fallback", "controlled-fallback-or-fail-on-missing-fonts");
        strategy.put("cssFamilies", properties.effectiveCssFamilies());
        strategy.put("pdfFamily", properties.effectivePdfFamily());
        strategy.put("resourceLocations", properties.effectiveFontPaths());
        return strategy;
    }

    public FontRegistrationReport registerPdfFonts(PdfRendererBuilder builder) {
        if (!properties.isEnabled()) {
            return new FontRegistrationReport(false, 0, List.of(), List.of("Font governance disabled"));
        }
        List<String> registered = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String location : properties.effectiveFontPaths()) {
            Resource resource = resolveResource(location);
            if (!resource.exists()) {
                skipped.add(location + " (missing)");
                continue;
            }
            try {
                registerFont(builder, location, resource);
                registered.add(location);
            } catch (Exception ex) {
                skipped.add(location + " (" + ex.getMessage() + ")");
            }
        }
        if (registered.isEmpty()) {
            String message = "No configured resume render font was found; falling back to renderer/system fonts. "
                    + "Configure career.render.font.paths with a licensed TTF/OTF/TTC font for stable CJK output.";
            if (properties.isFailOnMissingFonts()) {
                throw new ServiceException(message, BaseErrorCode.SERVICE_ERROR);
            }
            log.warn(message);
        }
        return new FontRegistrationReport(properties.isEnabled(), registered.size(), registered, skipped);
    }

    private void registerFont(PdfRendererBuilder builder, String location, Resource resource) throws IOException {
        if (isTrueTypeCollection(location)) {
            File file = resource.getFile();
            builder.useFont(file, properties.effectivePdfFamily());
            return;
        }
        builder.useFont(() -> openFontStream(location), properties.effectivePdfFamily());
    }

    private InputStream openFontStream(String location) {
        try {
            return resolveResource(location).getInputStream();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open resume render font: " + location, ex);
        }
    }

    private Resource resolveResource(String location) {
        String resolved = normalizeLocation(location);
        return resourceLoader.getResource(resolved);
    }

    private String normalizeLocation(String location) {
        String value = StrUtil.blankToDefault(location, "").trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith(FILE_PREFIX) || lower.startsWith(CLASSPATH_PREFIX)) {
            return value;
        }
        if (value.startsWith("/") || value.matches("^[A-Za-z]:[/\\\\].*")) {
            return FILE_PREFIX + value.replace("\\", "/");
        }
        return CLASSPATH_PREFIX + value;
    }

    private boolean isTrueTypeCollection(String location) {
        return StrUtil.endWithIgnoreCase(StrUtil.blankToDefault(location, "").trim(), ".ttc");
    }

    private String formatCssFamily(String family) {
        String value = family.trim();
        if ("serif".equalsIgnoreCase(value) || "sans-serif".equalsIgnoreCase(value) || "monospace".equalsIgnoreCase(value)) {
            return value;
        }
        return "'" + value.replace("'", "\\'") + "'";
    }

    public record FontRegistrationReport(boolean enabled, int registeredCount, List<String> registered, List<String> skipped) {
    }
}
