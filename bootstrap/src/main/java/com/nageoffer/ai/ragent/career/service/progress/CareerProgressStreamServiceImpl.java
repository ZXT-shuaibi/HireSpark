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

package com.nageoffer.ai.ragent.career.service.progress;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.CareerProgressEventDO;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class CareerProgressStreamServiceImpl implements CareerProgressStreamService {

    private static final long STREAM_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
    private static final String BUSINESS_TYPE_OPTIMIZATION = "OPTIMIZATION";
    private static final String BUSINESS_TYPE_INTERVIEW = "INTERVIEW";
    private static final Set<String> TERMINAL_EVENT_TYPES = Set.of(
            "PASSED", "NEEDS_REVIEW", "FAILED", "SESSION_FINISHED", "SESSION_CANCELLED");

    private final ConcurrentMap<String, CopyOnWriteArrayList<StreamClient>> clients = new ConcurrentHashMap<>();

    /**
     * 订阅简历优化任务的实时进度流。
     */
    @Override
    public SseEmitter subscribeOptimization(String taskId, String userId) {
        return subscribe(BUSINESS_TYPE_OPTIMIZATION, taskId, userId);
    }

    /**
     * 订阅面试会话的实时进度流。
     */
    @Override
    public SseEmitter subscribeInterview(String sessionId, String userId) {
        return subscribe(BUSINESS_TYPE_INTERVIEW, sessionId, userId);
    }

    /**
     * 将已落库的简历优化进度推送给在线订阅者。
     */
    @Override
    public void publishOptimization(CareerProgressEventDO event) {
        if (event == null || StrUtil.isBlank(event.getTaskId()) || StrUtil.isBlank(event.getUserId())) {
            return;
        }
        String key = buildKey(BUSINESS_TYPE_OPTIMIZATION, event.getTaskId(), event.getUserId());
        CopyOnWriteArrayList<StreamClient> currentClients = clients.get(key);
        if (currentClients == null || currentClients.isEmpty()) {
            return;
        }
        Map<String, Object> payload = toPayload(event);
        for (StreamClient client : currentClients) {
            sendProgress(client, payload, event.getEventType());
        }
    }

    /**
     * 发布已落库的面试进度事件。
     */
    @Override
    public void publishInterview(String sessionId, String userId, String eventType, Object payload) {
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(userId)) {
            return;
        }
        String key = buildKey(BUSINESS_TYPE_INTERVIEW, sessionId, userId);
        CopyOnWriteArrayList<StreamClient> currentClients = clients.get(key);
        if (currentClients == null || currentClients.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("userId", userId);
        body.put("eventType", eventType);
        body.put("payload", payload);
        for (StreamClient client : currentClients) {
            sendEvent(client, "progress", body, false);
            if (TERMINAL_EVENT_TYPES.contains(eventType)) {
                sendEvent(client, "done", body, true);
            }
        }
    }

    /**
     * 创建业务进度订阅并发送连接确认事件。
     */
    private SseEmitter subscribe(String businessType, String businessId, String userId) {
        if (StrUtil.isBlank(businessId)) {
            throw new ClientException("业务 ID 不能为空");
        }
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("用户 ID 不能为空");
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        String key = buildKey(businessType, businessId, userId);
        StreamClient client = new StreamClient(key, emitter);
        clients.computeIfAbsent(key, unused -> new CopyOnWriteArrayList<>()).add(client);
        Runnable cleanup = () -> removeClient(client);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        sendConnected(client, businessType, businessId);
        return emitter;
    }

    /**
     * 发送连接成功事件，便于前端确认流通道可用。
     */
    private void sendConnected(StreamClient client, String businessType, String businessId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessType", businessType);
        payload.put("businessId", businessId);
        payload.put("connected", true);
        sendEvent(client, "connected", payload, false);
    }

    /**
     * 发送单条进度事件，并在终态事件后关闭连接。
     */
    private void sendProgress(StreamClient client, Map<String, Object> payload, String eventType) {
        sendEvent(client, "progress", payload, false);
        if (TERMINAL_EVENT_TYPES.contains(eventType)) {
            sendEvent(client, "done", payload, true);
        }
    }

    /**
     * 发送 SSE 事件并处理断开的订阅者。
     */
    private void sendEvent(StreamClient client, String eventName, Object payload, boolean completeAfterSend) {
        if (client.closed().get()) {
            return;
        }
        synchronized (client.emitter()) {
            try {
                client.emitter().send(SseEmitter.event().name(eventName).data(payload));
                if (completeAfterSend && client.closed().compareAndSet(false, true)) {
                    client.emitter().complete();
                    removeClient(client);
                }
            } catch (IOException | IllegalStateException ex) {
                client.closed().set(true);
                removeClient(client);
                log.warn("Career 进度 SSE 推送失败，key={}", client.key(), ex);
            }
        }
    }

    /**
     * 从在线订阅表中移除失效客户端。
     */
    private void removeClient(StreamClient client) {
        CopyOnWriteArrayList<StreamClient> currentClients = clients.get(client.key());
        if (currentClients == null) {
            return;
        }
        currentClients.remove(client);
        if (currentClients.isEmpty()) {
            clients.remove(client.key(), currentClients);
        }
    }

    /**
     * 构建同一用户同一业务对象的订阅键。
     */
    private String buildKey(String businessType, String businessId, String userId) {
        return businessType + ':' + userId + ':' + businessId;
    }

    /**
     * 将数据库进度事件转换成前端消费的稳定载荷。
     */
    private Map<String, Object> toPayload(CareerProgressEventDO event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getId());
        payload.put("eventType", event.getEventType());
        payload.put("message", event.getMessage());
        payload.put("payloadJson", event.getPayloadJson());
        payload.put("createTime", event.getCreateTime());
        return payload;
    }

    private record StreamClient(String key, SseEmitter emitter, AtomicBoolean closed) {

        /**
         * 创建默认未关闭的 SSE 客户端。
         */
        private StreamClient(String key, SseEmitter emitter) {
            this(key, emitter, new AtomicBoolean(false));
        }
    }
}
