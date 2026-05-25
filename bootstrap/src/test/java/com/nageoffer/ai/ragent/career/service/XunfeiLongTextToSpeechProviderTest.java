package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.tts.CareerTextToSpeechProperties;
import com.nageoffer.ai.ragent.career.service.tts.CareerTextToSpeechProviderRequest;
import com.nageoffer.ai.ragent.career.service.tts.XunfeiLongTextToSpeechProvider;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XunfeiLongTextToSpeechProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void xunfeiProviderDownloadsAudioWhenTaskSucceeds() throws IOException {
        startServer();
        mockText("/create", taskResponse("task-1", "1", "", ""));
        mockText("/query", taskResponse("task-1", "5", base64(serverUrl("/audio")), base64(serverUrl("/pybuf"))));
        mockBytes("/audio", "mock-audio".getBytes(StandardCharsets.UTF_8));
        mockText("/pybuf", "ni hao");

        var result = new XunfeiLongTextToSpeechProvider(properties()).synthesize(request());

        assertThat(result.success()).isTrue();
        assertThat(result.completed()).isTrue();
        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.taskStatus()).isEqualTo("5");
        assertThat(result.audioBase64()).isEqualTo(base64("mock-audio"));
        assertThat(result.audioUrl()).isEqualTo(serverUrl("/audio"));
        assertThat(result.pybufContent()).isEqualTo("ni hao");
    }

    @Test
    void xunfeiProviderThrowsWhenTaskFails() throws IOException {
        startServer();
        mockText("/create", taskResponse("task-1", "1", "", ""));
        mockText("/query", taskResponse("task-1", "4", "", ""));

        assertThatThrownBy(() -> new XunfeiLongTextToSpeechProvider(properties()).synthesize(request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("task failed");
    }

    @Test
    void xunfeiProviderReturnsPendingWhenPollingExhausted() throws IOException {
        startServer();
        mockText("/create", taskResponse("task-1", "1", "", ""));
        mockText("/query", taskResponse("task-1", "1", "", ""));
        CareerTextToSpeechProperties properties = properties();
        properties.getXunfei().setMaxPolls(1);
        properties.getXunfei().setPollIntervalMillis(1);

        var result = new XunfeiLongTextToSpeechProvider(properties).synthesize(request());

        assertThat(result.success()).isTrue();
        assertThat(result.completed()).isFalse();
        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.taskStatus()).isEqualTo("1");
        assertThat(result.message()).contains("still running");
    }

    @Test
    void xunfeiProviderRejectsIncompleteCredentialsBeforeHttpCall() throws IOException {
        startServer();
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/create", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{}".getBytes(StandardCharsets.UTF_8), "application/json");
        });
        CareerTextToSpeechProperties properties = properties();
        properties.getXunfei().setApiSecret("");

        assertThatThrownBy(() -> new XunfeiLongTextToSpeechProvider(properties).synthesize(request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("credentials");
        assertThat(calls).hasValue(0);
    }

    private CareerTextToSpeechProperties properties() {
        CareerTextToSpeechProperties properties = new CareerTextToSpeechProperties();
        CareerTextToSpeechProperties.Xunfei xunfei = properties.getXunfei();
        xunfei.setAppId("app-id");
        xunfei.setApiKey("api-key");
        xunfei.setApiSecret("api-secret");
        xunfei.setEndpointBaseUrl(serverUrl(""));
        xunfei.setCreatePath("/create");
        xunfei.setQueryPath("/query");
        xunfei.setMaxPolls(3);
        xunfei.setPollIntervalMillis(1);
        return properties;
    }

    private CareerTextToSpeechProviderRequest request() {
        return new CareerTextToSpeechProviderRequest("session-1", "turn-1", "hello",
                List.of("hello"), "default", "cache-key", "cancel-key");
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    private void mockText(String path, String body) {
        server.createContext(path, exchange ->
                respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8), "application/json"));
    }

    private void mockBytes(String path, byte[] body) {
        server.createContext(path, exchange -> respond(exchange, 200, body, "application/octet-stream"));
    }

    private void respond(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String taskResponse(String taskId, String status, String audioUrl, String pybufUrl) {
        return """
                {
                  "header": {
                    "code": 0,
                    "message": "ok",
                    "task_id": "%s",
                    "task_status": "%s"
                  },
                  "payload": {
                    "audio": {"audio": "%s"},
                    "pybuf": {"text": "%s"}
                  }
                }
                """.formatted(taskId, status, audioUrl, pybufUrl);
    }

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
