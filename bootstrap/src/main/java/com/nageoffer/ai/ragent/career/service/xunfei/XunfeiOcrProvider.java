package com.nageoffer.ai.ragent.career.service.xunfei;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.xunfei.ocr", name = "enabled", havingValue = "true")
public class XunfeiOcrProvider extends AbstractXunfeiSignedHttpProvider {

    private static final String PROVIDER = "xunfei-ocr";

    private final XunfeiVisionNlpProperties properties;

    public XunfeiOcrResult recognize(XunfeiOcrRequest request) {
        XunfeiVisionNlpProperties.HttpFeature feature = properties.getOcr();
        validateCredentials(feature, "Xunfei OCR");
        validateRequest(feature, request);
        JsonNode response = postSigned(feature, buildBody(feature, request), "Xunfei OCR");
        List<String> lines = extractLines(structuredPayload(response));
        return new XunfeiOcrResult(PROVIDER, sid(response), String.join("\n", lines), lines, rawJson(response));
    }

    private ObjectNode buildBody(XunfeiVisionNlpProperties.HttpFeature feature, XunfeiOcrRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("header")
                .put("app_id", feature.getAppId().trim())
                .put("trace_id", StrUtil.blankToDefault(request.traceId(), ""));
        body.putObject("parameter").putObject(StrUtil.blankToDefault(feature.getService(), "ocr"))
                .put("result_encoding", "utf8");
        ObjectNode image = body.putObject("payload").putObject("image");
        image.put("encoding", "base64");
        image.put("format", StrUtil.blankToDefault(request.imageFormat(), "jpg"));
        image.put("image", request.imageBase64());
        return body;
    }

    private void validateRequest(XunfeiVisionNlpProperties.HttpFeature feature, XunfeiOcrRequest request) {
        if (request == null || StrUtil.isBlank(request.imageBase64())) {
            throw new ServiceException("OCR image must not be blank");
        }
        byte[] image = decodeImage(request.imageBase64());
        if (image.length > feature.effectiveMaxImageBytes()) {
            throw new ServiceException("OCR image exceeds Xunfei limit: " + feature.effectiveMaxImageBytes());
        }
    }

    private List<String> extractLines(JsonNode node) {
        List<String> lines = new ArrayList<>();
        collectText(node, lines);
        return lines;
    }

    private void collectText(JsonNode node, List<String> lines) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addLine(lines, node.asText());
            return;
        }
        if (node.isObject() && node.has("text") && node.get("text").isTextual()) {
            addLine(lines, node.get("text").asText());
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                collectText(child, lines);
            }
        }
    }

    private void addLine(List<String> lines, String value) {
        String text = StrUtil.blankToDefault(value, "").trim();
        if (StrUtil.isNotBlank(text) && !lines.contains(text)) {
            lines.add(text);
        }
    }

    private byte[] decodeImage(String imageBase64) {
        String payload = imageBase64;
        int comma = payload.indexOf(',');
        if (comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        return Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8));
    }
}
