package com.nageoffer.ai.ragent.career.service.xunfei;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XunfeiVisionNlpProviderTest {

    private HttpServer server;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ocrProviderSignsRequestAndExtractsTextLines() throws Exception {
        startServer("/ocr", xunfeiResponse("sid-ocr", """
                {"lines":[{"text":"Java backend"},{"words":[{"text":"Redis cache"}]}]}
                """));
        XunfeiVisionNlpProperties properties = properties();

        XunfeiOcrResult result = new XunfeiOcrProvider(properties).recognize(
                new XunfeiOcrRequest(base64("mock-image"), "jpg", "trace-ocr"));

        assertThat(result.provider()).isEqualTo("xunfei-ocr");
        assertThat(result.sid()).isEqualTo("sid-ocr");
        assertThat(result.text()).isEqualTo("Java backend\nRedis cache");
        assertThat(result.lines()).containsExactly("Java backend", "Redis cache");
        assertThat(lastPath.get()).contains("/ocr");
        assertThat(lastAuthorization.get()).isNotBlank();
        assertThat(lastBody.get()).contains(base64("mock-image"));
        assertThat(lastBody.get()).contains("trace-ocr");
    }

    @Test
    void faceDetectProviderNormalizesEmotionAndConfidence() throws Exception {
        startServer("/face", xunfeiResponse("sid-face", """
                {"faces":[{"emotion":"calm","confidence":0.90},{"emotion":"calm","confidence":0.80}]}
                """));
        XunfeiVisionNlpProperties properties = properties();

        XunfeiFaceDetectResult result = new XunfeiFaceDetectProvider(properties).detect(
                new XunfeiFaceDetectRequest(base64("mock-face"), "jpg", "trace-face"));

        assertThat(result.provider()).isEqualTo("xunfei-face-detect");
        assertThat(result.sid()).isEqualTo("sid-face");
        assertThat(result.faceCount()).isEqualTo(2);
        assertThat(result.dominantEmotion()).isEqualTo("calm");
        assertThat(result.averageConfidence()).isEqualTo(0.85D);
        assertThat(lastPath.get()).contains("/face");
        assertThat(lastBody.get()).contains(base64("mock-face"));
    }

    @Test
    void nlpProviderExtractsKeywordsEntitiesAndSentiment() throws Exception {
        startServer("/nlp", xunfeiResponse("sid-nlp", """
                {"keywords":["Java","Redis"],"entities":["Spring Boot"],"sentiment":"positive"}
                """));
        XunfeiVisionNlpProperties properties = properties();

        XunfeiNlpResult result = new XunfeiNlpProvider(properties).analyze(
                new XunfeiNlpRequest("Java Redis backend", "trace-nlp"));

        assertThat(result.provider()).isEqualTo("xunfei-nlp");
        assertThat(result.sid()).isEqualTo("sid-nlp");
        assertThat(result.keywords()).containsExactly("Java", "Redis");
        assertThat(result.entities()).containsExactly("Spring Boot");
        assertThat(result.sentiment()).isEqualTo("positive");
        assertThat(lastPath.get()).contains("/nlp");
        assertThat(lastBody.get()).contains(base64("Java Redis backend"));
    }

    @Test
    void providerRejectsMissingCredentialsBeforeHttpCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        startServer("/ocr", "{}");
        server.removeContext("/ocr");
        server.createContext("/ocr", exchange -> {
            calls.incrementAndGet();
            writeText(exchange, "{}");
        });
        XunfeiVisionNlpProperties properties = properties();
        properties.getOcr().setApiSecret("");

        assertThatThrownBy(() -> new XunfeiOcrProvider(properties).recognize(
                new XunfeiOcrRequest(base64("mock-image"), "jpg", "trace-ocr")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("credentials");
        assertThat(calls).hasValue(0);
    }

    private XunfeiVisionNlpProperties properties() {
        XunfeiVisionNlpProperties properties = new XunfeiVisionNlpProperties();
        configure(properties.getOcr(), "/ocr", "ocr");
        configure(properties.getFaceDetect(), "/face", "face_detect");
        configure(properties.getNlp(), "/nlp", "nlp");
        return properties;
    }

    private void configure(XunfeiVisionNlpProperties.HttpFeature feature, String path, String service) {
        feature.setEnabled(true);
        feature.setAppId("app-id");
        feature.setApiKey("api-key");
        feature.setApiSecret("api-secret");
        feature.setHost("127.0.0.1:" + server.getAddress().getPort());
        feature.setEndpointBaseUrl(serverUrl(""));
        feature.setPath(path);
        feature.setService(service);
        feature.setTimeoutSeconds(5);
    }

    private void startServer(String path, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeText(exchange, responseBody);
        });
        server.start();
    }

    private void writeText(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String xunfeiResponse(String sid, String decodedPayloadJson) {
        return """
                {
                  "header": {"code": 0, "message": "success", "sid": "%s"},
                  "payload": {
                    "result": {
                      "text": "%s"
                    }
                  }
                }
                """.formatted(sid, base64(decodedPayloadJson));
    }

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
