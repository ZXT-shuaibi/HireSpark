package com.nageoffer.ai.ragent.career.media;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XunfeiSparkIatFileTranscriptionAgentTest {

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
    void transcribesAudioFileWithSignedHttpRequest() throws Exception {
        startServer("""
                {
                  "header": {"code": 0, "message": "success", "sid": "sid-1"},
                  "payload": {
                    "result": {
                      "text": "%s"
                    }
                  }
                }
                """.formatted(base64("你好，欢迎面试")));
        XunfeiSparkIatFileTranscriptionAgent agent = new XunfeiSparkIatFileTranscriptionAgent(properties());

        CareerFileTranscriptionResult result = agent.transcribe(new CareerFileTranscriptionRequest(
                "answer.wav",
                "audio/wav",
                "mock-audio".getBytes(StandardCharsets.UTF_8),
                "zh_cn",
                "trace-1"));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.text()).isEqualTo("你好，欢迎面试");
        assertThat(result.provider()).isEqualTo("xunfei-spark-iat");
        assertThat(lastPath.get()).contains("/iat");
        assertThat(lastAuthorization.get()).isNotBlank();
        assertThat(lastBody.get()).contains(base64("mock-audio"));
    }

    @Test
    void rejectsMissingCredentialsBeforeHttpCall() throws IOException {
        startServer("{}");
        XunfeiSparkIatProperties properties = properties();
        properties.setApiSecret("");
        XunfeiSparkIatFileTranscriptionAgent agent = new XunfeiSparkIatFileTranscriptionAgent(properties);

        assertThatThrownBy(() -> agent.transcribe(new CareerFileTranscriptionRequest(
                "answer.wav",
                "audio/wav",
                "mock-audio".getBytes(StandardCharsets.UTF_8),
                "zh_cn",
                "trace-1")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("credentials");
    }

    private XunfeiSparkIatProperties properties() {
        XunfeiSparkIatProperties properties = new XunfeiSparkIatProperties();
        properties.setEnabled(true);
        properties.setAppId("app-id");
        properties.setApiKey("api-key");
        properties.setApiSecret("api-secret");
        properties.setHost("127.0.0.1:" + server.getAddress().getPort());
        properties.setEndpointBaseUrl(serverUrl(""));
        properties.setTranscribePath("/iat");
        properties.setMaxAudioBytes(1024);
        return properties;
    }

    private void startServer(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/iat", exchange -> {
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

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
