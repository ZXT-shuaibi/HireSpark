package com.nageoffer.ai.ragent.career.service.render;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.docx4j.convert.in.xhtml.XHTMLImporterImpl;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ResumeRenderPipeline {

    public static final String TEMPLATE_VERSION = "career-resume-template-v1";

    private static final String EXPORT_TYPE_MARKDOWN = "MARKDOWN";
    private static final String EXPORT_TYPE_HTML = "HTML";
    private static final String EXPORT_TYPE_PDF = "PDF";
    private static final String EXPORT_TYPE_WORD = "WORD";
    private static final String CONTENT_TYPE_MARKDOWN = "text/markdown";
    private static final String CONTENT_TYPE_HTML = "text/html";
    private static final String CONTENT_TYPE_PDF = "application/pdf";
    private static final String CONTENT_TYPE_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String RENDER_ENGINE_COMMONMARK = "commonmark";
    private static final String RENDER_ENGINE_OPENHTMLTOPDF = "openhtmltopdf";
    private static final String RENDER_ENGINE_DOCX4J = "docx4j";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
    private final ResumeTemplateRenderer templateRenderer;
    private final ResumeRenderFontRegistry fontRegistry;

    /**
     * 兼容不启动 Spring 容器的单元测试，使用默认模板渲染器。
     */
    public ResumeRenderPipeline() {
        this(new ResumeTemplateRenderer(), new ResumeRenderFontRegistry(
                new ResumeRenderFontProperties(), new DefaultResourceLoader()));
    }

    /**
     * 注入简历模板渲染器，统一生成导出前的 Markdown。
     */
    public ResumeRenderPipeline(ResumeTemplateRenderer templateRenderer) {
        this(templateRenderer, new ResumeRenderFontRegistry(
                new ResumeRenderFontProperties(), new DefaultResourceLoader()));
    }

    @Autowired
    public ResumeRenderPipeline(ResumeTemplateRenderer templateRenderer, ResumeRenderFontRegistry fontRegistry) {
        this.templateRenderer = templateRenderer == null ? new ResumeTemplateRenderer() : templateRenderer;
        this.fontRegistry = fontRegistry == null
                ? new ResumeRenderFontRegistry(new ResumeRenderFontProperties(), new DefaultResourceLoader())
                : fontRegistry;
    }

    /**
     * 校验简历版本是否具备指定格式的渲染前置条件。
     */
    public ResumeRenderValidationResult validate(ResumeVersionDO version, String exportType) {
        String type = StrUtil.blankToDefault(exportType, "").trim().toUpperCase(Locale.ROOT);
        String traceId = "career-render-" + (version == null ? "unknown" : version.getId()) + "-" + type.toLowerCase();
        List<String> missingFields = missingFields(version);
        List<String> warnings = new ArrayList<>();
        boolean supported = EXPORT_TYPE_MARKDOWN.equals(type)
                || EXPORT_TYPE_HTML.equals(type)
                || EXPORT_TYPE_PDF.equals(type)
                || EXPORT_TYPE_WORD.equals(type);
        if (!supported) {
            warnings.add("Unsupported export type: " + type);
        }
        return ResumeRenderValidationResult.builder()
                .valid(supported && missingFields.isEmpty())
                .missingFields(missingFields)
                .templateVersion(TEMPLATE_VERSION)
                .contentType(contentType(type))
                .warnings(warnings)
                .traceId(traceId)
                .rendererEnabled(supported)
                .disabledReason(supported ? null : "Export type is not supported: " + type)
                .renderEngine(renderEngine(type))
                .fontFamily(fontRegistry.cssFontFamily())
                .pdfFontFamily(fontRegistry.pdfFontFamily())
                .fontResourceLocations(fontRegistry.fontResourceLocations())
                .build();
    }

    /**
     * 将简历 Markdown 渲染成指定导出格式的文件产物。
     */
    public ResumeRenderOutput render(ResumeVersionDO version, String exportType) {
        ResumeRenderValidationResult validation = validate(version, exportType);
        if (!validation.valid() || !validation.rendererEnabled()) {
            throw new ClientException(StrUtil.blankToDefault(validation.disabledReason(), "简历渲染校验失败"));
        }
        String type = StrUtil.blankToDefault(exportType, "").trim().toUpperCase(Locale.ROOT);
        String markdown = templateRenderer.render(version);
        return switch (type) {
            case EXPORT_TYPE_MARKDOWN -> new ResumeRenderOutput(fileName(version, "md"), CONTENT_TYPE_MARKDOWN,
                    markdown.getBytes(StandardCharsets.UTF_8));
            case EXPORT_TYPE_HTML -> {
                String html = buildHtmlDocument(version, markdown);
                yield new ResumeRenderOutput(fileName(version, "html"), CONTENT_TYPE_HTML, html.getBytes(StandardCharsets.UTF_8));
            }
            case EXPORT_TYPE_PDF -> new ResumeRenderOutput(fileName(version, "pdf"), CONTENT_TYPE_PDF,
                    renderPdf(buildHtmlDocument(version, markdown)));
            case EXPORT_TYPE_WORD -> new ResumeRenderOutput(fileName(version, "docx"), CONTENT_TYPE_DOCX,
                    renderDocx(buildHtmlDocument(version, markdown)));
            default -> throw new ClientException("不支持的简历导出格式: " + type);
        };
    }

    /**
     * 根据导出类型返回实际内容类型，写入校验结果便于追溯。
     */
    private String contentType(String type) {
        return switch (type) {
            case EXPORT_TYPE_MARKDOWN -> CONTENT_TYPE_MARKDOWN;
            case EXPORT_TYPE_HTML -> CONTENT_TYPE_HTML;
            case EXPORT_TYPE_PDF -> CONTENT_TYPE_PDF;
            case EXPORT_TYPE_WORD -> CONTENT_TYPE_DOCX;
            default -> "";
        };
    }

    /**
     * 将 Markdown 正文转换为完整的 HTML/XHTML 页面。
     */
    private String renderEngine(String type) {
        return switch (type) {
            case EXPORT_TYPE_MARKDOWN, EXPORT_TYPE_HTML -> RENDER_ENGINE_COMMONMARK;
            case EXPORT_TYPE_PDF -> RENDER_ENGINE_OPENHTMLTOPDF;
            case EXPORT_TYPE_WORD -> RENDER_ENGINE_DOCX4J;
            default -> "";
        };
    }

    public String buildHtmlDocument(ResumeVersionDO version, String markdown) {
        Node document = markdownParser.parse(markdown);
        String body = htmlRenderer.render(document);
        String title = escapeHtml(version == null ? "Resume" : StrUtil.blankToDefault(version.getTitle(), "Resume"));
        return """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta charset="utf-8" />
                    <title>%s</title>
                    <style>
                        body { font-family: %s; line-height: 1.6; color: #222; margin: 36px; }
                        h1, h2, h3 { color: #111; margin-bottom: 0.35em; }
                        p { margin: 0 0 0.85em; }
                        ul, ol { margin-top: 0; }
                    </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, fontRegistry.cssFontFamily(), body);
    }

    /**
     * 使用 openhtmltopdf 将 HTML 页面渲染为 PDF 字节流。
     */
    public byte[] renderPdf(String html) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            fontRegistry.registerPdfFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new ServiceException("简历 PDF 渲染失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 使用 docx4j 将 HTML 页面转换为 DOCX 字节流。
     */
    public byte[] renderDocx(String html) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
            XHTMLImporterImpl importer = new XHTMLImporterImpl(wordPackage);
            wordPackage.getMainDocumentPart().getContent().addAll(importer.convert(html, null));
            wordPackage.save(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new ServiceException("简历 DOCX 渲染失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 生成导出文件名，保持和历史文件命名一致。
     */
    private String fileName(ResumeVersionDO version, String extension) {
        return "resume-" + version.getId() + "." + extension;
    }

    /**
     * 检查简历版本是否缺少基础结构化字段。
     */
    private List<String> missingFields(ResumeVersionDO version) {
        List<String> missing = new ArrayList<>();
        if (version == null) {
            missing.add("resumeVersion");
            return missing;
        }
        if (StrUtil.isBlank(version.getContentJson()) && StrUtil.isBlank(version.getMarkdownContent())) {
            missing.add("content");
            return missing;
        }
        if (StrUtil.isBlank(version.getContentJson())) {
            return missing;
        }
        try {
            JsonNode root = objectMapper.readTree(version.getContentJson());
            JsonNode basic = root.path("basic");
            if ((basic.isMissingNode() || StrUtil.isBlank(basic.path("name").asText(null)))
                    && StrUtil.isBlank(version.getTitle())) {
                missing.add("basic.name");
            }
        } catch (Exception ex) {
            missing.add("contentJson");
        }
        return missing;
    }

    /**
     * 转义 HTML 标题中的特殊字符，避免标题破坏页面结构。
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
