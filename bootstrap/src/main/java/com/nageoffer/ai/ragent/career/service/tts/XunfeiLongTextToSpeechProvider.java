package com.nageoffer.ai.ragent.career.service.tts;

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
@ConditionalOnProperty(prefix = "career.tts.xunfei", name = "enabled", havingValue = "true")
public class XunfeiLongTextToSpeechProvider implements CareerTextToSpeechProvider {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final CareerTextToSpeechProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public CareerTextToSpeechProviderResult synthesize(CareerTextToSpeechProviderRequest request) {
        CareerTextToSpeechProperties.Xunfei config = properties.getXunfei();
        validate(config, request);
        JsonNode created = postWithSign(config, config.getCreatePath(), buildCreateBody(config, request));
        TtsTask task = parseTask(created);
        if (!Integer.valueOf(0).equals(task.code()) || StrUtil.isBlank(task.taskId())) {
            throw new ServiceException("Failed to create Xunfei TTS task: " + task.message());
        }
        long deadline = System.currentTimeMillis() + Math.max(10, config.getTimeoutSeconds()) * 1000L;
        TtsTask latest = task;
        while (System.currentTimeMillis() < deadline) {
            latest = parseTask(postWithSign(config, config.getQueryPath(), buildQueryBody(config, task.taskId())));
            if ("5".equals(latest.taskStatus())) {
                return CareerTextToSpeechProviderResult.success(latest.taskId(), latest.taskStatus(),
                        downloadBinaryAsBase64(latest.audioUrl()), latest.audioUrl(), downloadText(latest.pybufUrl()));
            }
            if ("2".equals(latest.taskStatus()) || "4".equals(latest.taskStatus())) {
                throw new ServiceException("Xunfei TTS task failed, taskId=" + task.taskId()
                        + ", status=" + latest.taskStatus() + ", message=" + latest.message());
            }
            sleepQuietly(config.getPollIntervalMillis());
        }
        return CareerTextToSpeechProviderResult.pending(task.taskId(), latest.taskStatus(),
                "Xunfei TTS task is still running");
    }

    private void validate(CareerTextToSpeechProperties.Xunfei config, CareerTextToSpeechProviderRequest request) {
        if (config == null || StrUtil.hasBlank(config.getAppId(), config.getApiKey(), config.getApiSecret())) {
            throw new ServiceException("Xunfei TTS credentials are missing");
        }
        if (request == null || StrUtil.isBlank(request.text())) {
            throw new ServiceException("TTS text must not be blank");
        }
        if (request.text().length() > config.getMaxTextChars()) {
            throw new ServiceException("TTS text length exceeds Xunfei limit: " + config.getMaxTextChars());
        }
    }

    private ObjectNode buildCreateBody(CareerTextToSpeechProperties.Xunfei config,
                                       CareerTextToSpeechProviderRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode header = body.putObject("header");
        header.put("app_id", config.getAppId().trim());

        ObjectNode dts = body.putObject("parameter").putObject("dts");
        dts.put("vcn", StrUtil.blankToDefault(request.voice(), "x4_mingge"));
        dts.put("language", StrUtil.blankToDefault(config.getLanguage(), "zh"));
        dts.put("speed", config.getSpeed());
        dts.put("volume", config.getVolume());
        dts.put("pitch", config.getPitch());
        dts.put("rhy", config.getRhy());
        ObjectNode audio = dts.putObject("audio");
        audio.put("encoding", StrUtil.blankToDefault(config.getAudioEncoding(), "lame"));
        audio.put("sample_rate", config.getSampleRate());
        ObjectNode pybuf = dts.putObject("pybuf");
        pybuf.put("encoding", "utf8");
        pybuf.put("compress", "raw");
        pybuf.put("format", "plain");

        ObjectNode text = body.putObject("payload").putObject("text");
        text.put("encoding", "utf8");
        text.put("compress", "raw");
        text.put("format", "plain");
        text.put("text", Base64.getEncoder().encodeToString(request.text().getBytes(StandardCharsets.UTF_8)));
        return body;
    }

    private ObjectNode buildQueryBody(CareerTextToSpeechProperties.Xunfei config, String taskId) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode header = body.putObject("header");
        header.put("app_id", config.getAppId().trim());
        header.put("task_id", taskId);
        return body;
    }

    private JsonNode postWithSign(CareerTextToSpeechProperties.Xunfei config, String path, JsonNode body) {
        SignedRequest signed = signedRequest(config, path);
        Request request = new Request.Builder()
                .url(signed.url())
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .addHeader("Host", config.getHost())
                .addHeader("Date", signed.date())
                .addHeader("x-date", signed.date())
                .addHeader("Authorization", signed.authorization())
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ServiceException("Xunfei TTS request failed, HTTP status: " + response.code());
            }
            if (StrUtil.isBlank(responseBody)) {
                throw new ServiceException("Xunfei TTS response is empty");
            }
            return objectMapper.readTree(responseBody);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to call Xunfei TTS: " + ex.getMessage());
        }
    }

    private SignedRequest signedRequest(CareerTextToSpeechProperties.Xunfei config, String path) {
        try {
            String host = config.getHost().trim();
            String date = HTTP_DATE_FORMATTER.format(Instant.now());
            String requestLine = "POST " + path + " HTTP/1.1";
            String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;
            String signature = hmacSha256Base64(signatureOrigin, config.getApiSecret().trim());
            String authorizationOrigin = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    config.getApiKey().trim(), signature);
            String authorization = Base64.getEncoder()
                    .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
            HttpUrl url = new HttpUrl.Builder()
                    .scheme("https")
                    .host(host)
                    .encodedPath(path)
                    .addQueryParameter("host", host)
                    .addQueryParameter("date", date)
                    .addQueryParameter("authorization", authorization)
                    .build();
            return new SignedRequest(url.toString(), date, authorization);
        } catch (Exception ex) {
            throw new ServiceException("Failed to build Xunfei TTS auth URL: " + ex.getMessage());
        }
    }

    private String hmacSha256Base64(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private TtsTask parseTask(JsonNode response) {
        JsonNode header = response == null ? null : response.get("header");
        JsonNode payload = response == null ? null : response.get("payload");
        String audioUrl = decodeUrl(payload == null ? null : payload.path("audio").path("audio").asText(null));
        String pybufUrl = decodeUrl(payload == null ? null : payload.path("pybuf").path("text").asText(null));
        return new TtsTask(
                header == null ? -1 : header.path("code").asInt(-1),
                header == null ? "empty response" : header.path("message").asText("unknown"),
                header == null ? null : header.path("task_id").asText(null),
                header == null ? null : header.path("task_status").asText(null),
                audioUrl,
                pybufUrl);
    }

    private String downloadBinaryAsBase64(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new ServiceException("Failed to download TTS audio");
            }
            return Base64.getEncoder().encodeToString(response.body().bytes());
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to download TTS audio: " + ex.getMessage());
        }
    }

    private String downloadText(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new ServiceException("Failed to download TTS pybuf");
            }
            return response.body().string();
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to download TTS pybuf: " + ex.getMessage());
        }
    }

    private String decodeUrl(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(500, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Polling for TTS task was interrupted");
        }
    }

    private record SignedRequest(String url, String date, String authorization) {
    }

    private record TtsTask(Integer code,
                           String message,
                           String taskId,
                           String taskStatus,
                           String audioUrl,
                           String pybufUrl) {
    }
}
