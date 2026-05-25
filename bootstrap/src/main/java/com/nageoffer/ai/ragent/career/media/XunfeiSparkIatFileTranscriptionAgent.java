package com.nageoffer.ai.ragent.career.media;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.voice.xunfei-iat", name = "enabled", havingValue = "true")
public class XunfeiSparkIatFileTranscriptionAgent implements CareerFileTranscriptionAgent {

    private static final String PROVIDER = "xunfei-spark-iat";
    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final XunfeiSparkIatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public CareerFileTranscriptionResult transcribe(CareerFileTranscriptionRequest request) {
        validate(request);
        long startedAt = System.currentTimeMillis();
        JsonNode response = postWithSign(buildBody(request));
        int code = response.path("header").path("code").asInt(response.path("code").asInt(-1));
        if (code != 0) {
            String message = response.path("header").path("message").asText(response.path("message").asText("unknown"));
            throw new ServiceException("Xunfei Spark IAT failed: " + message);
        }
        String sid = response.path("header").path("sid").asText(response.path("sid").asText(null));
        String text = parseText(response);
        return CareerFileTranscriptionResult.succeeded(PROVIDER, text, sid, request.traceId(),
                System.currentTimeMillis() - startedAt);
    }

    private void validate(CareerFileTranscriptionRequest request) {
        if (StrUtil.hasBlank(properties.getAppId(), properties.getApiKey(), properties.getApiSecret())) {
            throw new ServiceException("Xunfei Spark IAT credentials are missing");
        }
        if (request == null || request.audioBytes() == null || request.audioBytes().length == 0) {
            throw new ServiceException("Audio file must not be empty");
        }
        if (request.audioBytes().length > properties.effectiveMaxAudioBytes()) {
            throw new ServiceException("Audio file exceeds Xunfei Spark IAT limit: " + properties.effectiveMaxAudioBytes());
        }
    }

    private ObjectNode buildBody(CareerFileTranscriptionRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("header")
                .put("app_id", properties.getAppId().trim())
                .put("trace_id", StrUtil.blankToDefault(request.traceId(), ""));
        ObjectNode iat = body.putObject("parameter").putObject("iat");
        iat.put("domain", StrUtil.blankToDefault(properties.getDomain(), "iat"));
        iat.put("language", StrUtil.blankToDefault(request.language(), properties.getLanguage()));
        iat.put("accent", StrUtil.blankToDefault(properties.getAccent(), "mandarin"));
        ObjectNode audio = body.putObject("payload").putObject("audio");
        audio.put("encoding", StrUtil.blankToDefault(properties.getAudioEncoding(), "raw"));
        audio.put("format", StrUtil.blankToDefault(properties.getAudioFormat(), "audio/L16;rate=16000"));
        audio.put("audio", Base64.getEncoder().encodeToString(request.audioBytes()));
        return body;
    }

    private JsonNode postWithSign(JsonNode body) {
        SignedRequest signed = signedRequest();
        Request request = new Request.Builder()
                .url(signed.url())
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .addHeader("Host", properties.getHost())
                .addHeader("Date", signed.date())
                .addHeader("x-date", signed.date())
                .addHeader("Authorization", signed.authorization())
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ServiceException("Xunfei Spark IAT request failed, HTTP status: " + response.code());
            }
            if (StrUtil.isBlank(responseBody)) {
                throw new ServiceException("Xunfei Spark IAT response is empty");
            }
            return objectMapper.readTree(responseBody);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to call Xunfei Spark IAT: " + ex.getMessage());
        }
    }

    private SignedRequest signedRequest() {
        try {
            String host = properties.getHost().trim();
            String path = properties.getTranscribePath();
            String date = HTTP_DATE_FORMATTER.format(Instant.now());
            String requestLine = "POST " + path + " HTTP/1.1";
            String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;
            String signature = hmacSha256Base64(signatureOrigin, properties.getApiSecret().trim());
            String authorizationOrigin = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    properties.getApiKey().trim(), signature);
            String authorization = Base64.getEncoder()
                    .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
            return new SignedRequest(buildEndpointUrl(host, date, authorization).toString(), date, authorization);
        } catch (Exception ex) {
            throw new ServiceException("Failed to build Xunfei Spark IAT auth URL: " + ex.getMessage());
        }
    }

    private HttpUrl buildEndpointUrl(String host, String date, String authorization) {
        HttpUrl.Builder builder;
        if (StrUtil.isNotBlank(properties.getEndpointBaseUrl())) {
            HttpUrl baseUrl = HttpUrl.parse(properties.getEndpointBaseUrl());
            if (baseUrl == null) {
                throw new ServiceException("Xunfei Spark IAT endpoint base URL is invalid");
            }
            builder = baseUrl.newBuilder().encodedPath(properties.getTranscribePath());
        } else {
            builder = new HttpUrl.Builder()
                    .scheme("https")
                    .host(host)
                    .encodedPath(properties.getTranscribePath());
        }
        return builder
                .addQueryParameter("host", host)
                .addQueryParameter("date", date)
                .addQueryParameter("authorization", authorization)
                .build();
    }

    private String parseText(JsonNode response) {
        String payloadText = response.path("payload").path("result").path("text").asText("");
        if (StrUtil.isNotBlank(payloadText)) {
            return decodeBase64OrRaw(payloadText);
        }
        String dataText = response.path("data").path("text").asText(response.path("text").asText(""));
        return decodeBase64OrRaw(dataText);
    }

    private String decodeBase64OrRaw(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private String hmacSha256Base64(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private record SignedRequest(String url, String date, String authorization) {
    }
}
