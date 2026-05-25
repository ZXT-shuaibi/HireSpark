/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.media;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于讯飞 AST WebSocket 的实时转写适配器，默认关闭，配置密钥后接入 Career 语音面试链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "career.voice.xunfei-ast", name = "enabled", havingValue = "true")
public class XunfeiAstAudioTranscriptionService implements CareerAudioTranscriptionService {

    private final XunfeiAstProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient webSocketClient = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    private final ExecutorService senderExecutor = Executors.newCachedThreadPool(
            ThreadFactoryBuilder.create().setNamePrefix("career-xunfei-ast-sender-").build());

    /**
     * 消费浏览器推送的 PCM 音频流，并把 AST 增量结果转换为统一分段事件。
     */
    @Override
    public CompletableFuture<String> transcribe(InputStream audioInputStream,
                                                Consumer<AstTranscriptionSegment> segmentConsumer) {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (audioInputStream == null) {
            future.completeExceptionally(new IllegalArgumentException("audioInputStream cannot be null"));
            return future;
        }
        if (!hasCredentials()) {
            future.completeExceptionally(new IllegalStateException("讯飞 AST 配置不完整"));
            return future;
        }
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String wsUrl;
        try {
            wsUrl = buildAstUrl(sessionId);
        } catch (Exception ex) {
            future.completeExceptionally(new IllegalStateException("构建讯飞 AST 鉴权地址失败", ex));
            return future;
        }
        AtomicInteger fallbackSegmentId = new AtomicInteger();
        AtomicReference<String> latestText = new AtomicReference<>("");
        CareerAstTranscriptionAssembler assembler = new CareerAstTranscriptionAssembler();
        Request request = new Request.Builder().url(wsUrl).build();
        webSocketClient.newWebSocket(request, new WebSocketListener() {

            /**
             * WebSocket 建连后启动音频发送线程，避免阻塞 OkHttp 回调线程。
             */
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                senderExecutor.execute(() -> sendAudioStream(webSocket, audioInputStream, sessionId, future));
            }

            /**
             * 解析 AST 增量包，并转换为 Career 内部的分段事件。
             */
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode root = objectMapper.readTree(text);
                    if (isAstError(root)) {
                        completeExceptionally(future, webSocket, new IllegalStateException("讯飞 AST 返回错误：" + text));
                        return;
                    }
                    JsonNode st = extractAstSt(root);
                    String partialText = extractAstText(root);
                    boolean finalPacket = isAstFinal(root);
                    if (StrUtil.isNotBlank(partialText)) {
                        AstTranscriptionSegment segment = new AstTranscriptionSegment(
                                resolveSegmentId(root, st, fallbackSegmentId),
                                textValue(st, "pgs"),
                                extractRg(st),
                                intValue(st, "bg"),
                                intValue(st, "ed"),
                                partialText,
                                finalPacket
                        );
                        latestText.set(assembler.apply(segment).fullText());
                        segmentConsumer.accept(segment);
                    }
                    if (finalPacket && !future.isDone()) {
                        future.complete(latestText.get());
                        webSocket.close(1000, "completed");
                    }
                } catch (RuntimeException ex) {
                    completeExceptionally(future, webSocket, ex);
                } catch (Exception ex) {
                    completeExceptionally(future, webSocket, new IllegalStateException("解析讯飞 AST 响应失败", ex));
                }
            }

            /**
             * 连接失败时结束转写 future，交给 WebSocket 层通知前端。
             */
            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                if (!future.isDone()) {
                    future.completeExceptionally(new IllegalStateException("讯飞 AST WebSocket 调用失败", throwable));
                }
            }
        });
        return future;
    }

    /**
     * 关闭音频发送线程池。
     */
    @PreDestroy
    public void shutdown() {
        senderExecutor.shutdownNow();
    }

    /**
     * 判断讯飞 AST 所需密钥是否完整。
     */
    private boolean hasCredentials() {
        return StrUtil.isNotBlank(properties.getAppId())
                && StrUtil.isNotBlank(properties.getApiKey())
                && StrUtil.isNotBlank(properties.getApiSecret());
    }

    /**
     * 将浏览器侧 PCM 分片转发给讯飞 AST，并在输入结束后发送结束标记。
     */
    private void sendAudioStream(WebSocket webSocket,
                                 InputStream audioInputStream,
                                 String sessionId,
                                 CompletableFuture<String> future) {
        byte[] buffer = new byte[properties.effectiveChunkSizeBytes()];
        try (InputStream in = audioInputStream) {
            int read;
            while (!future.isCancelled() && (read = in.read(buffer)) != -1) {
                ByteString chunk = ByteString.of(read == buffer.length ? buffer : Arrays.copyOf(buffer, read));
                if (!webSocket.send(chunk)) {
                    throw new IllegalStateException("讯飞 AST 音频分片发送失败");
                }
                Thread.sleep(properties.effectiveSendIntervalMs());
            }
            if (!future.isCancelled()) {
                webSocket.send("{\"end\":true,\"sessionId\":\"" + sessionId + "\"}");
            } else {
                webSocket.close(1000, "cancelled");
            }
        } catch (Exception ex) {
            completeExceptionally(future, webSocket, new IllegalStateException("发送讯飞 AST 音频流失败", ex));
        }
    }

    /**
     * 构建讯飞 AST 鉴权 WebSocket 地址。
     */
    String buildAstUrl(String sessionId) throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("appId", properties.getAppId().trim());
        params.put("accessKeyId", properties.getApiKey().trim());
        params.put("audio_encode", valueOrDefault(properties.getAudioEncode(), "pcm_s16le"));
        params.put("lang", valueOrDefault(properties.getLang(), "autodialect"));
        params.put("samplerate", valueOrDefault(properties.getSampleRate(), "16000"));
        params.put("sessionId", sessionId);
        params.put("utc", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")));
        params.put("uuid", UUID.randomUUID().toString().replace("-", ""));

        String signTarget = buildCanonicalQuery(params);
        params.put("signature", hmacSha1Base64(signTarget, properties.getApiSecret().trim()));
        return properties.effectiveWsBaseUrl() + "?" + buildCanonicalQuery(params);
    }

    /**
     * 从 AST 响应中提取 st 节点，兼容 data.cn.st、data.st、cn.st 和 st 四种形态。
     */
    JsonNode extractAstSt(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        JsonNode data = root.path("data");
        JsonNode dataCnSt = data.path("cn").path("st");
        if (dataCnSt.isObject()) {
            return dataCnSt;
        }
        JsonNode dataSt = data.path("st");
        if (dataSt.isObject()) {
            return dataSt;
        }
        JsonNode cnSt = root.path("cn").path("st");
        if (cnSt.isObject()) {
            return cnSt;
        }
        JsonNode st = root.path("st");
        return st.isObject() ? st : null;
    }

    /**
     * 提取 AST 文本，仅读取第一组候选的最佳词，避免候选词重复拼接。
     */
    String extractAstText(JsonNode root) {
        JsonNode st = extractAstSt(root);
        if (st == null) {
            return "";
        }
        JsonNode rt = st.path("rt");
        if (!rt.isArray() || rt.isEmpty()) {
            return "";
        }
        JsonNode ws = rt.get(0).path("ws");
        if (!ws.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode word : ws) {
            JsonNode cw = word.path("cw");
            if (cw.isArray() && !cw.isEmpty()) {
                builder.append(cw.get(0).path("w").asText(""));
            }
        }
        return builder.toString();
    }

    /**
     * 解析 AST 分段 ID，缺失时使用本地递增序号兜底。
     */
    int resolveSegmentId(JsonNode root, JsonNode st, AtomicInteger fallbackSegmentId) {
        Integer segmentId = firstNonNull(
                intValue(root.path("data"), "seg_id"),
                intValue(st, "seg_id"),
                intValue(root, "seg_id"),
                intValue(root.path("data"), "sn"),
                intValue(st, "sn"));
        if (segmentId != null) {
            fallbackSegmentId.set(Math.max(fallbackSegmentId.get(), segmentId));
            return segmentId;
        }
        return fallbackSegmentId.incrementAndGet();
    }

    /**
     * 提取 AST 替换范围 rg。
     */
    int[] extractRg(JsonNode st) {
        if (st == null) {
            return null;
        }
        JsonNode rg = st.path("rg");
        if (!rg.isArray() || rg.size() < 2) {
            return null;
        }
        return new int[]{rg.get(0).asInt(), rg.get(1).asInt()};
    }

    /**
     * 判断当前 AST 包是否为最终包。
     */
    boolean isAstFinal(JsonNode root) {
        JsonNode data = root == null ? null : root.path("data");
        if (booleanValue(data, "ls")) {
            return true;
        }
        JsonNode st = extractAstSt(root);
        return booleanValue(st, "ls") || intValue(data, "status") != null && intValue(data, "status") == 2;
    }

    /**
     * 判断 AST 响应是否为业务错误。
     */
    private boolean isAstError(JsonNode root) {
        String action = textValue(root, "action");
        String code = textValue(root, "code");
        return "error".equalsIgnoreCase(action) || StrUtil.isNotBlank(code) && !"0".equals(code);
    }

    /**
     * 异常结束 future 并关闭 AST WebSocket。
     */
    private void completeExceptionally(CompletableFuture<String> future, WebSocket webSocket, RuntimeException ex) {
        if (!future.isDone()) {
            future.completeExceptionally(ex);
        }
        if (webSocket != null) {
            webSocket.close(1011, "failed");
        }
    }

    /**
     * 构建按键排序的签名原文或查询字符串。
     */
    private String buildCanonicalQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return builder.toString();
    }

    /**
     * 使用 HMAC-SHA1 生成讯飞 AST 签名。
     */
    private String hmacSha1Base64(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 对查询参数进行 UTF-8 URL 编码。
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 读取字符串字段。
     */
    private String textValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /**
     * 读取整数字段。
     */
    private Integer intValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    /**
     * 读取布尔字段。
     */
    private boolean booleanValue(JsonNode node, String field) {
        return node != null && node.path(field).asBoolean(false);
    }

    /**
     * 返回第一个非空值。
     */
    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 读取非空配置，否则返回默认值。
     */
    private String valueOrDefault(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value.trim();
    }
}
