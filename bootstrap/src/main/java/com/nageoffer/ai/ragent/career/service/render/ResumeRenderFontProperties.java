package com.nageoffer.ai.ragent.career.service.render;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.render.font")
public class ResumeRenderFontProperties {

    private boolean enabled = true;

    private String pdfFamily = "Noto Sans SC";

    private boolean failOnMissingFonts = false;

    private List<String> cssFamilies = new ArrayList<>(List.of(
            "Noto Sans SC",
            "Noto Sans CJK SC",
            "Microsoft YaHei",
            "SimHei",
            "SimSun",
            "Arial",
            "sans-serif"
    ));

    private List<String> paths = new ArrayList<>(List.of(
            "classpath:/fonts/NotoSansSC-Regular.ttf",
            "file:C:/Windows/Fonts/NotoSansSC-VF.ttf",
            "file:C:/Windows/Fonts/simhei.ttf",
            "file:/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf",
            "file:/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "file:/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.otf",
            "file:/usr/local/share/fonts/NotoSansSC-Regular.ttf",
            "file:/System/Library/Fonts/PingFang.ttc"
    ));

    public String effectivePdfFamily() {
        return StrUtil.blankToDefault(pdfFamily, "Noto Sans SC").trim();
    }

    public List<String> effectiveCssFamilies() {
        List<String> families = normalize(cssFamilies);
        if (families.isEmpty()) {
            return List.of("Noto Sans SC", "Noto Sans CJK SC", "Microsoft YaHei", "SimSun", "Arial", "sans-serif");
        }
        return families;
    }

    public List<String> effectiveFontPaths() {
        return normalize(paths);
    }

    private List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
