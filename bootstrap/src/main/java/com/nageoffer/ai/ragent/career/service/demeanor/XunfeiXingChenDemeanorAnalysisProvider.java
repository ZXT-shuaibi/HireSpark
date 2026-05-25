package com.nageoffer.ai.ragent.career.service.demeanor;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.demeanor.xingchen", name = "enabled", havingValue = "true")
public class XunfeiXingChenDemeanorAnalysisProvider implements CareerDemeanorAnalysisProvider {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType PNG_MEDIA_TYPE = MediaType.parse("image/png");

    private final CareerDemeanorAnalysisProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CareerDemeanorAnalysisProviderResult analyze(CareerDemeanorAnalysisProviderRequest request) {
        CareerDemeanorAnalysisProperties.XingChen config = properties.getXingchen();
        validate(config, request);
        String fileUrl = StrUtil.blankToDefault(request.imageUrl(), "");
        if (StrUtil.isBlank(fileUrl) && StrUtil.isNotBlank(request.imageBase64())) {
            fileUrl = uploadImage(config, request.imageBase64());
        }
        JsonNode response = postWorkflow(config, request, fileUrl);
        return parseResponse(response);
    }

    private void validate(CareerDemeanorAnalysisProperties.XingChen config,
                          CareerDemeanorAnalysisProviderRequest request) {
        if (config == null || StrUtil.hasBlank(config.getApiKey(), config.getApiSecret(), config.getFlowId())) {
            throw new ServiceException("XingChen demeanor credentials are missing");
        }
        if (request == null || (StrUtil.isBlank(request.imageUrl()) && StrUtil.isBlank(request.imageBase64()))) {
            throw new ServiceException("Demeanor image must not be blank");
        }
    }

    private JsonNode postWorkflow(CareerDemeanorAnalysisProperties.XingChen config,
                                  CareerDemeanorAnalysisProviderRequest request,
                                  String fileUrl) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("flow_id", config.getFlowId().trim());
        body.put("uid", StrUtil.blankToDefault(request.sessionId(), "ragent-demeanor"));
        body.put("stream", false);
        body.put("chat_id", StrUtil.blankToDefault(request.sessionId(), "demeanor-" + System.currentTimeMillis()));
        body.putArray("history");
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("AGENT_USER_INPUT",
                "Evaluate this interview image and return panicLevel, seriousnessLevel, "
                        + "emoticonHandling and compositeScore as 0-100 integers.");
        parameters.put("USER_FILE", fileUrl);
        if (StrUtil.isNotBlank(request.sampledAt())) {
            parameters.put("SAMPLED_AT", request.sampledAt());
        }

        Request httpRequest = new Request.Builder()
                .url(config.getChatUrl())
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", bearer(config))
                .build();
        return executeJson(config, httpRequest, "XingChen demeanor workflow request failed");
    }

    private String uploadImage(CareerDemeanorAnalysisProperties.XingChen config, String imageBase64) {
        byte[] image = decodeImage(imageBase64);
        if (image.length > config.getMaxImageBytes()) {
            throw new ServiceException("Demeanor image exceeds XingChen upload limit");
        }
        RequestBody fileBody = RequestBody.create(image, PNG_MEDIA_TYPE);
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "demeanor.png", fileBody)
                .build();
        Request request = new Request.Builder()
                .url(config.getUploadUrl())
                .post(body)
                .addHeader("Authorization", bearer(config))
                .build();
        JsonNode response = executeJson(config, request, "XingChen demeanor image upload failed");
        JsonNode code = response.path("code");
        if (!code.isMissingNode() && code.asInt(-1) != 0) {
            throw new ServiceException("XingChen demeanor image upload failed: "
                    + response.path("message").asText("unknown"));
        }
        String fileUrl = response.path("data").path("url").asText("");
        if (StrUtil.isBlank(fileUrl)) {
            throw new ServiceException("XingChen demeanor image upload response missing url");
        }
        return fileUrl;
    }

    private JsonNode executeJson(CareerDemeanorAnalysisProperties.XingChen config,
                                 Request request,
                                 String failureMessage) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Math.max(5, config.getTimeoutSeconds()), TimeUnit.SECONDS)
                .readTimeout(Math.max(5, config.getTimeoutSeconds()), TimeUnit.SECONDS)
                .writeTimeout(Math.max(5, config.getTimeoutSeconds()), TimeUnit.SECONDS)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ServiceException(failureMessage + ", HTTP status: " + response.code());
            }
            if (StrUtil.isBlank(responseBody)) {
                throw new ServiceException(failureMessage + ", empty response");
            }
            return objectMapper.readTree(responseBody);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(failureMessage + ": " + ex.getMessage());
        }
    }

    private CareerDemeanorAnalysisProviderResult parseResponse(JsonNode response) {
        JsonNode structured = extractStructuredContent(response);
        Integer panicLevel = score(structured, "panicLevel");
        Integer seriousnessLevel = score(structured, "seriousnessLevel");
        Integer emoticonHandling = score(structured, "emoticonHandling");
        Integer compositeScore = score(structured, "compositeScore");
        if (panicLevel == null || seriousnessLevel == null
                || emoticonHandling == null || compositeScore == null) {
            throw new ServiceException("XingChen demeanor response missing required score fields");
        }
        boolean tenScale = List.of(panicLevel, seriousnessLevel, emoticonHandling, compositeScore).stream()
                .allMatch(score -> score >= 0 && score <= 10);
        int normalizedPanicLevel = normalizeScore(panicLevel, tenScale);
        int normalizedSeriousnessLevel = normalizeScore(seriousnessLevel, tenScale);
        int normalizedEmoticonHandling = normalizeScore(emoticonHandling, tenScale);
        int normalizedCompositeScore = normalizeScore(compositeScore, tenScale);
        List<String> signals = new ArrayList<>();
        signals.add("panic-level:" + normalizedPanicLevel);
        signals.add("seriousness-level:" + normalizedSeriousnessLevel);
        signals.add("emoticon-handling:" + normalizedEmoticonHandling);
        return new CareerDemeanorAnalysisProviderResult(
                normalizedPanicLevel,
                normalizedSeriousnessLevel,
                normalizedEmoticonHandling,
                normalizedCompositeScore,
                signals,
                "xingchen-workflow-ok");
    }

    private JsonNode extractStructuredContent(JsonNode response) {
        JsonNode direct = findObjectContaining(response, "panicLevel");
        if (direct != null) {
            return direct;
        }
        JsonNode content = findField(response, "content");
        if (content != null && content.isTextual()) {
            try {
                return objectMapper.readTree(content.asText());
            } catch (Exception ignored) {
                throw new ServiceException("XingChen demeanor content is not structured JSON");
            }
        }
        return response;
    }

    private JsonNode findObjectContaining(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.has(fieldName)) {
            return node;
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                JsonNode found = findObjectContaining(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode findField(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.has(fieldName)) {
            return node.get(fieldName);
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                JsonNode found = findField(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Integer score(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            String text = value.asText("");
            if (StrUtil.isBlank(text)) {
                return null;
            }
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private int normalizeScore(Integer score, boolean tenScale) {
        int value = score == null ? 0 : score;
        if (tenScale) {
            value = value * 10;
        }
        return Math.max(0, Math.min(100, value));
    }

    private byte[] decodeImage(String imageBase64) {
        String payload = imageBase64;
        int comma = payload.indexOf(',');
        if (comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new ServiceException("Demeanor imageBase64 is invalid");
        }
    }

    private String bearer(CareerDemeanorAnalysisProperties.XingChen config) {
        return "Bearer " + config.getApiKey().trim() + ":" + config.getApiSecret().trim();
    }
}
