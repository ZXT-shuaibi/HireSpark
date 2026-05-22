package com.nageoffer.ai.ragent.career.service.xunfei;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.xunfei.face-detect", name = "enabled", havingValue = "true")
public class XunfeiFaceDetectProvider extends AbstractXunfeiSignedHttpProvider {

    private static final String PROVIDER = "xunfei-face-detect";

    private final XunfeiVisionNlpProperties properties;

    public XunfeiFaceDetectResult detect(XunfeiFaceDetectRequest request) {
        XunfeiVisionNlpProperties.HttpFeature feature = properties.getFaceDetect();
        validateCredentials(feature, "Xunfei FaceDetect");
        validateRequest(feature, request);
        JsonNode response = postSigned(feature, buildBody(feature, request), "Xunfei FaceDetect");
        FaceSummary summary = summarizeFaces(structuredPayload(response));
        return new XunfeiFaceDetectResult(PROVIDER, sid(response), summary.faceCount(),
                summary.dominantEmotion(), summary.averageConfidence(), rawJson(response));
    }

    private ObjectNode buildBody(XunfeiVisionNlpProperties.HttpFeature feature, XunfeiFaceDetectRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("header")
                .put("app_id", feature.getAppId().trim())
                .put("trace_id", StrUtil.blankToDefault(request.traceId(), ""));
        body.putObject("parameter").putObject(StrUtil.blankToDefault(feature.getService(), "face_detect"))
                .put("result_encoding", "utf8");
        ObjectNode image = body.putObject("payload").putObject("image");
        image.put("encoding", "base64");
        image.put("format", StrUtil.blankToDefault(request.imageFormat(), "jpg"));
        image.put("image", request.imageBase64());
        return body;
    }

    private void validateRequest(XunfeiVisionNlpProperties.HttpFeature feature, XunfeiFaceDetectRequest request) {
        if (request == null || StrUtil.isBlank(request.imageBase64())) {
            throw new ServiceException("FaceDetect image must not be blank");
        }
        byte[] image = decodeImage(request.imageBase64());
        if (image.length > feature.effectiveMaxImageBytes()) {
            throw new ServiceException("FaceDetect image exceeds Xunfei limit: " + feature.effectiveMaxImageBytes());
        }
    }

    private FaceSummary summarizeFaces(JsonNode structuredPayload) {
        JsonNode faces = findField(structuredPayload, "faces");
        if (faces == null || !faces.isArray()) {
            int faceCount = intField(structuredPayload, "faceCount", intField(structuredPayload, "face_count", 0));
            String emotion = textField(structuredPayload, "emotion", "");
            double confidence = doubleField(structuredPayload, "confidence", 0D);
            return new FaceSummary(faceCount, emotion, round2(confidence));
        }

        int faceCount = 0;
        double confidenceSum = 0D;
        Map<String, Integer> emotionCounts = new LinkedHashMap<>();
        for (JsonNode face : faces) {
            faceCount++;
            String emotion = firstTextField(face, "emotion", "expression", "emotionLabel", "emotion_label");
            if (StrUtil.isNotBlank(emotion)) {
                emotionCounts.merge(emotion, 1, Integer::sum);
            }
            confidenceSum += firstDoubleField(face, "confidence", "score", "probability");
        }
        String dominantEmotion = emotionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        double averageConfidence = faceCount == 0 ? 0D : round2(confidenceSum / faceCount);
        return new FaceSummary(faceCount, dominantEmotion, averageConfidence);
    }

    private String firstTextField(JsonNode node, String... names) {
        for (String name : names) {
            String value = textField(node, name, "");
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private double firstDoubleField(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
            if (value != null && value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText());
                } catch (NumberFormatException ignored) {
                    return 0D;
                }
            }
        }
        return 0D;
    }

    private int intField(JsonNode node, String name, int fallback) {
        JsonNode value = findField(node, name);
        if (value == null || value.isMissingNode()) {
            return fallback;
        }
        return value.asInt(fallback);
    }

    private String textField(JsonNode node, String name, String fallback) {
        JsonNode value = findField(node, name);
        if (value == null || value.isMissingNode()) {
            return fallback;
        }
        return value.asText(fallback);
    }

    private double doubleField(JsonNode node, String name, double fallback) {
        JsonNode value = findField(node, name);
        if (value == null || value.isMissingNode()) {
            return fallback;
        }
        return value.asDouble(fallback);
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private byte[] decodeImage(String imageBase64) {
        String payload = imageBase64;
        int comma = payload.indexOf(',');
        if (comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        return Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8));
    }

    private record FaceSummary(int faceCount, String dominantEmotion, double averageConfidence) {
    }
}
