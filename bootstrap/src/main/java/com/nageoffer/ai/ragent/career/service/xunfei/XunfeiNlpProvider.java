package com.nageoffer.ai.ragent.career.service.xunfei;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.xunfei.nlp", name = "enabled", havingValue = "true")
public class XunfeiNlpProvider extends AbstractXunfeiSignedHttpProvider {

    private static final String PROVIDER = "xunfei-nlp";

    private final XunfeiVisionNlpProperties properties;

    public XunfeiNlpResult analyze(XunfeiNlpRequest request) {
        XunfeiVisionNlpProperties.HttpFeature feature = properties.getNlp();
        validateCredentials(feature, "Xunfei NLP");
        validateRequest(feature, request);
        JsonNode response = postSigned(feature, buildBody(feature, request), "Xunfei NLP");
        JsonNode structured = structuredPayload(response);
        return new XunfeiNlpResult(
                PROVIDER,
                sid(response),
                stringList(findField(structured, "keywords")),
                stringList(findField(structured, "entities")),
                firstText(structured, "sentiment", "polarity"),
                rawJson(response));
    }

    private ObjectNode buildBody(XunfeiVisionNlpProperties.HttpFeature feature, XunfeiNlpRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("header")
                .put("app_id", feature.getAppId().trim())
                .put("trace_id", StrUtil.blankToDefault(request.traceId(), ""));
        body.putObject("parameter").putObject(StrUtil.blankToDefault(feature.getService(), "nlp"))
                .put("result_encoding", "utf8");
        ObjectNode text = body.putObject("payload").putObject("text");
        text.put("encoding", "utf8");
        text.put("compress", "raw");
        text.put("format", "plain");
        text.put("text", base64(request.text()));
        return body;
    }

    private void validateRequest(XunfeiVisionNlpProperties.HttpFeature feature, XunfeiNlpRequest request) {
        if (request == null || StrUtil.isBlank(request.text())) {
            throw new ServiceException("NLP text must not be blank");
        }
        if (request.text().length() > feature.effectiveMaxTextChars()) {
            throw new ServiceException("NLP text exceeds Xunfei limit: " + feature.effectiveMaxTextChars());
        }
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                addText(values, item);
            }
            return values;
        }
        addText(values, node);
        return values;
    }

    private void addText(List<String> values, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            String value = node.asText("").trim();
            if (StrUtil.isNotBlank(value)) {
                values.add(value);
            }
            return;
        }
        JsonNode text = findField(node, "text");
        if (text == null) {
            text = findField(node, "name");
        }
        if (text != null) {
            String value = text.asText("").trim();
            if (StrUtil.isNotBlank(value)) {
                values.add(value);
            }
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = findField(node, name);
            if (value != null && value.isValueNode() && StrUtil.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }
}
