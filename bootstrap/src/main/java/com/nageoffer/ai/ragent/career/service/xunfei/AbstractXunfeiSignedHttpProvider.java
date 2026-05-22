package com.nageoffer.ai.ragent.career.service.xunfei;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

abstract class AbstractXunfeiSignedHttpProvider {

    protected static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected JsonNode postSigned(XunfeiVisionNlpProperties.HttpFeature feature,
                                  JsonNode body,
                                  String featureName) {
        SignedRequest signed = signedRequest(feature);
        Request request = new Request.Builder()
                .url(signed.url())
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .addHeader("Host", feature.getHost())
                .addHeader("Date", signed.date())
                .addHeader("x-date", signed.date())
                .addHeader("Authorization", signed.authorization())
                .addHeader("Content-Type", "application/json")
                .build();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(feature.effectiveTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(feature.effectiveTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(feature.effectiveTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ServiceException(featureName + " request failed, HTTP status: " + response.code());
            }
            if (StrUtil.isBlank(responseBody)) {
                throw new ServiceException(featureName + " response is empty");
            }
            JsonNode responseJson = objectMapper.readTree(responseBody);
            int code = responseJson.path("header").path("code").asInt(responseJson.path("code").asInt(0));
            if (code != 0) {
                String message = responseJson.path("header").path("message")
                        .asText(responseJson.path("message").asText("unknown"));
                throw new ServiceException(featureName + " failed: " + message);
            }
            return responseJson;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to call " + featureName + ": " + ex.getMessage());
        }
    }

    protected void validateCredentials(XunfeiVisionNlpProperties.HttpFeature feature, String featureName) {
        if (feature == null || StrUtil.hasBlank(feature.getAppId(), feature.getApiKey(), feature.getApiSecret())) {
            throw new ServiceException(featureName + " credentials are missing");
        }
    }

    protected String sid(JsonNode response) {
        return response.path("header").path("sid").asText(response.path("sid").asText(""));
    }

    protected String rawJson(JsonNode response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            return "";
        }
    }

    protected JsonNode structuredPayload(JsonNode response) {
        String payloadText = response.path("payload").path("result").path("text").asText("");
        if (StrUtil.isBlank(payloadText)) {
            payloadText = response.path("payload").path("text").asText(
                    response.path("data").path("text").asText(response.path("text").asText("")));
        }
        String decoded = decodeBase64OrRaw(payloadText);
        if (StrUtil.isBlank(decoded)) {
            return response;
        }
        try {
            return objectMapper.readTree(decoded);
        } catch (Exception ex) {
            return objectMapper.getNodeFactory().textNode(decoded);
        }
    }

    protected String base64(String value) {
        return Base64.getEncoder().encodeToString(
                StrUtil.blankToDefault(value, "").getBytes(StandardCharsets.UTF_8));
    }

    protected String decodeBase64OrRaw(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    protected JsonNode findField(JsonNode node, String fieldName) {
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

    private SignedRequest signedRequest(XunfeiVisionNlpProperties.HttpFeature feature) {
        try {
            String host = feature.getHost().trim();
            String path = feature.getPath();
            String date = HTTP_DATE_FORMATTER.format(Instant.now());
            String requestLine = "POST " + path + " HTTP/1.1";
            String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;
            String signature = hmacSha256Base64(signatureOrigin, feature.getApiSecret().trim());
            String authorizationOrigin = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    feature.getApiKey().trim(), signature);
            String authorization = Base64.getEncoder()
                    .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
            return new SignedRequest(buildEndpointUrl(feature, host, date, authorization).toString(), date, authorization);
        } catch (Exception ex) {
            throw new ServiceException("Failed to build Xunfei auth URL: " + ex.getMessage());
        }
    }

    private HttpUrl buildEndpointUrl(XunfeiVisionNlpProperties.HttpFeature feature,
                                     String host,
                                     String date,
                                     String authorization) {
        HttpUrl.Builder builder;
        if (StrUtil.isNotBlank(feature.getEndpointBaseUrl())) {
            HttpUrl baseUrl = HttpUrl.parse(feature.getEndpointBaseUrl());
            if (baseUrl == null) {
                throw new ServiceException("Xunfei endpoint base URL is invalid");
            }
            builder = baseUrl.newBuilder().encodedPath(feature.getPath());
        } else {
            builder = new HttpUrl.Builder()
                    .scheme("https")
                    .host(host)
                    .encodedPath(feature.getPath());
        }
        return builder
                .addQueryParameter("host", host)
                .addQueryParameter("date", date)
                .addQueryParameter("authorization", authorization)
                .build();
    }

    private String hmacSha256Base64(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private record SignedRequest(String url, String date, String authorization) {
    }
}
