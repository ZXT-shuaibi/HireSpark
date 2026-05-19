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

package com.nageoffer.ai.ragent.career.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.dao.entity.CareerTaskAttemptDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerTaskAttemptMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerSingleFlightRecordMapper;
import com.nageoffer.ai.ragent.career.service.attempt.CareerTaskAttemptRecorder;
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardProperties;
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardService;
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardTimeoutException;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightHeartbeatManager;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightService;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLocalReplayCache;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmServiceImpl;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightMode;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightProperties;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightRedisCoordinator;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightServiceImpl;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerSingleFlightTest {

    @Mock
    private CareerSingleFlightRecordMapper recordMapper;

    @Mock
    private CareerTaskAttemptMapper attemptMapper;

    @Mock
    private LLMService llmService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final List<CareerSingleFlightRecordDO> records = new ArrayList<>();

    private final List<CareerTaskAttemptDO> attempts = new ArrayList<>();

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerSingleFlightRecordDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerTaskAttemptDO.class);
    }

    @Test
    void duplicateKeyReusesOwnerAndReplaysCompletedResult() {
        stubPersistence();
        CareerSingleFlightService service = newService();

        CareerSingleFlightService.AcquireResult first = service.tryAcquire("INTERVIEW_EVALUATE", "key-1", "owner-a", "trace-1");
        CareerSingleFlightService.AcquireResult duplicate = service.tryAcquire("INTERVIEW_EVALUATE", "key-1", "owner-b", "trace-2");

        assertTrue(first.owner());
        assertFalse(duplicate.owner());
        assertFalse(duplicate.replayAvailable());
        assertEquals(2, records.get(0).getRequestCount());

        assertTrue(service.completeSuccess("key-1", "owner-a", first.record().getFencingToken(), "{\"score\":88}"));
        CareerSingleFlightService.AcquireResult replay = service.tryAcquire("INTERVIEW_EVALUATE", "key-1", "owner-c", "trace-3");

        assertFalse(replay.owner());
        assertTrue(replay.replayAvailable());
        assertEquals("{\"score\":88}", service.replayIfAvailable("key-1").orElseThrow().getResultJson());
    }

    @Test
    void staleFencingTokenCannotOverwriteNewOwnerResult() {
        stubPersistence();
        CareerSingleFlightService service = newService();

        CareerSingleFlightService.AcquireResult first = service.tryAcquire("OPTIMIZATION", "key-2", "owner-a", "trace-1");
        assertTrue(service.completeFailure("key-2", "owner-a", first.record().getFencingToken(), "TIMEOUT"));
        CareerSingleFlightService.AcquireResult takeover = service.tryAcquire("OPTIMIZATION", "key-2", "owner-b", "trace-2");

        assertTrue(takeover.owner());
        assertEquals(2L, takeover.record().getFencingToken());
        assertFalse(service.completeSuccess("key-2", "owner-a", 1L, "{\"stale\":true}"));
        assertTrue(service.completeSuccess("key-2", "owner-b", 2L, "{\"fresh\":true}"));
        assertEquals("{\"fresh\":true}", service.replayIfAvailable("key-2").orElseThrow().getResultJson());
    }

    @Test
    void timedOutOwnerCanBeTakenOverWithNewFencingToken() {
        stubPersistence();
        CareerSingleFlightService service = newService();

        CareerSingleFlightService.AcquireResult first = service.tryAcquire("INTERVIEW_EVALUATE", "key-timeout", "owner-a", "trace-1");
        first.record().setHeartbeatTime(new Date(System.currentTimeMillis() - 300_000L));
        CareerSingleFlightService.AcquireResult takeover =
                service.tryAcquire("INTERVIEW_EVALUATE", "key-timeout", "owner-b", "trace-2");

        assertTrue(takeover.owner());
        assertEquals(2L, takeover.record().getFencingToken());
        assertFalse(service.completeSuccess("key-timeout", "owner-a", 1L, "{\"stale\":true}"));
        assertTrue(service.completeSuccess("key-timeout", "owner-b", 2L, "{\"fresh\":true}"));
    }

    @Test
    void duplicateColdInsertFallsBackToExistingRunningOwner() {
        CareerSingleFlightRecordDO existing = CareerSingleFlightRecordDO.builder()
                .id("sf-existing")
                .singleFlightKey("key-race")
                .scene("OPTIMIZATION")
                .ownerId("owner-a")
                .fencingToken(1L)
                .status("RUNNING")
                .heartbeatTime(new Date())
                .requestCount(1)
                .build();
        when(recordMapper.selectList(anyRecordWrapper())).thenReturn(List.of(), List.of(existing));
        when(recordMapper.insert(any(CareerSingleFlightRecordDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));
        when(recordMapper.updateById(existing)).thenReturn(1);

        CareerSingleFlightService.AcquireResult result =
                newService().tryAcquire("OPTIMIZATION", "key-race", "owner-b", "trace-2");

        assertFalse(result.owner());
        assertFalse(result.replayAvailable());
        assertEquals(2, existing.getRequestCount());
        verify(recordMapper).updateById(existing);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void redisCoordinatorMapsLuaStateWithoutRealRedis() {
        CareerSingleFlightRedisCoordinator coordinator =
                new CareerSingleFlightRedisCoordinator(stringRedisTemplate, newTestSingleFlightProperties());
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("OWNER", "RUNNING", "owner-a", "1", "1", "", "", "trace-1", "1000"))
                .thenReturn(List.of("0"))
                .thenReturn(List.of("REPLAY", "SUCCESS", "owner-b", "2", "3", "{\"fresh\":true}", "", "trace-2", "2000"));

        Optional<CareerSingleFlightRedisCoordinator.RedisState> owner =
                coordinator.acquire("OPTIMIZATION", "key-redis", "owner-a", "trace-1");
        Optional<Boolean> staleComplete =
                coordinator.completeSuccess("key-redis", "owner-a", 1L, "{\"stale\":true}");
        Optional<CareerSingleFlightRedisCoordinator.RedisState> replay =
                coordinator.replayIfAvailable("key-redis");

        assertTrue(owner.orElseThrow().owner());
        assertFalse(staleComplete.orElseThrow());
        assertTrue(replay.orElseThrow().replayAvailable());
        assertEquals(2L, replay.orElseThrow().fencingToken());
        assertEquals("{\"fresh\":true}", replay.orElseThrow().resultJson());
    }

    @Test
    void redisSingleFlightRejectsStaleFencingTokenBeforeDbAuditUpdate() {
        stubPersistence();
        CareerSingleFlightRedisCoordinator coordinator = mock(CareerSingleFlightRedisCoordinator.class);
        CareerSingleFlightProperties properties = newTestSingleFlightProperties();
        when(coordinator.acquire("OPTIMIZATION", "key-redis-fence", "owner-a", "trace-1"))
                .thenReturn(Optional.of(redisState(true, false, "OPTIMIZATION", "key-redis-fence",
                        "owner-a", 1L, "RUNNING", null, null, 1)));
        when(coordinator.completeFailure("key-redis-fence", "owner-a", 1L, "TIMEOUT"))
                .thenReturn(Optional.of(true));
        when(coordinator.acquire("OPTIMIZATION", "key-redis-fence", "owner-b", "trace-2"))
                .thenReturn(Optional.of(redisState(true, false, "OPTIMIZATION", "key-redis-fence",
                        "owner-b", 2L, "RUNNING", null, null, 2)));
        when(coordinator.completeSuccess("key-redis-fence", "owner-a", 1L, "{\"stale\":true}"))
                .thenReturn(Optional.of(false));
        when(coordinator.completeSuccess("key-redis-fence", "owner-b", 2L, "{\"fresh\":true}"))
                .thenReturn(Optional.of(true));
        CareerSingleFlightService service = new CareerSingleFlightServiceImpl(recordMapper, coordinator, properties);

        CareerSingleFlightService.AcquireResult first =
                service.tryAcquire("OPTIMIZATION", "key-redis-fence", "owner-a", "trace-1");
        assertTrue(service.completeFailure("key-redis-fence", "owner-a", first.record().getFencingToken(), "TIMEOUT"));
        CareerSingleFlightService.AcquireResult takeover =
                service.tryAcquire("OPTIMIZATION", "key-redis-fence", "owner-b", "trace-2");

        assertEquals(2L, takeover.record().getFencingToken());
        assertFalse(service.completeSuccess("key-redis-fence", "owner-a", 1L, "{\"stale\":true}"));
        assertTrue(service.completeSuccess("key-redis-fence", "owner-b", 2L, "{\"fresh\":true}"));
        assertEquals("{\"fresh\":true}",
                service.replayIfAvailable("key-redis-fence").orElseThrow().getResultJson());
    }

    @Test
    void redisFailureFallsBackToDbSingleFlight() {
        stubPersistence();
        CareerSingleFlightRedisCoordinator coordinator = mock(CareerSingleFlightRedisCoordinator.class);
        when(coordinator.acquire("INTERVIEW_EVALUATE", "key-redis-down", "owner-a", "trace-1"))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        CareerSingleFlightService service =
                new CareerSingleFlightServiceImpl(recordMapper, coordinator, newTestSingleFlightProperties());

        CareerSingleFlightService.AcquireResult result =
                service.tryAcquire("INTERVIEW_EVALUATE", "key-redis-down", "owner-a", "trace-1");

        assertTrue(result.owner());
        assertEquals(1, records.size());
        assertEquals("RUNNING", records.get(0).getStatus());
    }

    @Test
    void localModeNeverCallsRedisCoordinator() {
        stubPersistence();
        CareerSingleFlightRedisCoordinator coordinator = mock(CareerSingleFlightRedisCoordinator.class);
        CareerSingleFlightProperties properties = newTestSingleFlightProperties();
        properties.setMode(CareerSingleFlightMode.LOCAL);
        CareerSingleFlightService service =
                new CareerSingleFlightServiceImpl(recordMapper, coordinator, properties);

        CareerSingleFlightService.AcquireResult acquired =
                service.tryAcquire("OPTIMIZATION", "key-local", "owner-local", "trace-local");
        assertTrue(acquired.owner());
        assertTrue(service.completeSuccess("key-local", "owner-local", acquired.record().getFencingToken(),
                "{\"response\":\"local-result\"}"));
        assertEquals("{\"response\":\"local-result\"}",
                service.replayIfAvailable("key-local").orElseThrow().getResultJson());
        verifyNoInteractions(coordinator);
    }

    @Test
    void redisReplayMissDoesNotUseDbReplayWhenRedisIsAuthoritative() {
        CareerSingleFlightRedisCoordinator coordinator = mock(CareerSingleFlightRedisCoordinator.class);
        when(coordinator.replayIfAvailable("key-redis-authoritative")).thenReturn(Optional.empty());
        CareerSingleFlightService service =
                new CareerSingleFlightServiceImpl(recordMapper, coordinator, newTestSingleFlightProperties());

        assertTrue(service.replayIfAvailable("key-redis-authoritative").isEmpty());
        verify(recordMapper, never()).selectList(anyRecordWrapper());
    }

    @Test
    void redisOwnerResultSurvivesDbAuditFailure() {
        CareerSingleFlightRedisCoordinator coordinator = mock(CareerSingleFlightRedisCoordinator.class);
        when(coordinator.acquire("OPTIMIZATION", "key-audit-down", "owner-a", "trace-1"))
                .thenReturn(Optional.of(redisState(true, false, "OPTIMIZATION", "key-audit-down",
                        "owner-a", 7L, "RUNNING", null, null, 4)));
        when(recordMapper.selectList(anyRecordWrapper())).thenReturn(List.of());
        when(recordMapper.insert(any(CareerSingleFlightRecordDO.class)))
                .thenThrow(new RuntimeException("audit db down"));
        CareerSingleFlightService service =
                new CareerSingleFlightServiceImpl(recordMapper, coordinator, newTestSingleFlightProperties());

        CareerSingleFlightService.AcquireResult result =
                service.tryAcquire("OPTIMIZATION", "key-audit-down", "owner-a", "trace-1");

        assertTrue(result.owner());
        assertFalse(result.replayAvailable());
        assertEquals(7L, result.record().getFencingToken());
        assertEquals("RUNNING", result.record().getStatus());
    }

    @Test
    void llmWrapperPersistsAndReplaysAiResultWithoutSecondModelCall() {
        stubPersistence();
        stubAttemptPersistence();
        CareerSingleFlightLlmServiceImpl wrapper = newWrapper();
        when(llmService.chat(any(ChatRequest.class))).thenReturn("model-result");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("same prompt")))
                .temperature(0.1D)
                .build();

        String first = wrapper.chat("OPTIMIZATION_REVIEW", "same-key", "trace-1", request);
        String replay = wrapper.chat("OPTIMIZATION_REVIEW", "same-key", "trace-2", request);

        assertEquals("model-result", first);
        assertEquals("model-result", replay);
        verify(llmService).chat(any(ChatRequest.class));
        assertTrue(records.get(0).getResultJson().contains("model-result"));
        assertEquals(2, attempts.size());
        assertEquals("SUCCESS", attempts.get(0).getStatus());
        assertFalse(attempts.get(0).getReplayed());
        assertEquals("REPLAYED", attempts.get(1).getStatus());
        assertTrue(attempts.get(1).getReplayed());
    }

    @Test
    void llmWrapperReadsLocalReplayBeforeSingleFlightService() {
        stubAttemptPersistence();
        CareerSingleFlightService service = mock(CareerSingleFlightService.class);
        CareerSingleFlightProperties properties = newTestSingleFlightProperties();
        CareerSingleFlightLocalReplayCache localReplayCache = new CareerSingleFlightLocalReplayCache();
        CareerSingleFlightHeartbeatManager heartbeatManager = new CareerSingleFlightHeartbeatManager();
        String scene = "OPTIMIZATION_REVIEW";
        String rawKey = "l1-key";
        String stableKey = stableWrapperKey(scene, rawKey);
        localReplayCache.putSuccess(stableKey, CareerSingleFlightRecordDO.builder()
                .singleFlightKey(stableKey)
                .scene(scene)
                .status("SUCCESS")
                .resultJson("{\"response\":\"l1-result\"}")
                .build(), properties.localReplayTtlMillis());
        CareerSingleFlightLlmServiceImpl wrapper = new CareerSingleFlightLlmServiceImpl(
                service,
                llmService,
                new CareerTaskAttemptRecorder(attemptMapper),
                newGuardService(),
                properties,
                localReplayCache,
                heartbeatManager);
        try {
            String result = wrapper.chat(scene, rawKey, "trace-l1",
                    ChatRequest.builder().messages(List.of(ChatMessage.user("prompt"))).build());

            assertEquals("l1-result", result);
            verifyNoInteractions(service);
            verify(llmService, never()).chat(any(ChatRequest.class));
            assertEquals(1, attempts.size());
            assertEquals("REPLAYED", attempts.get(0).getStatus());
        } finally {
            heartbeatManager.shutdown();
        }
    }

    @Test
    void llmWrapperFollowerWaitsAndReplaysOwnerResult() {
        stubAttemptPersistence();
        CareerSingleFlightProperties properties = newTestSingleFlightProperties();
        properties.setWaitTimeout(Duration.ofMillis(120));
        properties.setPollInterval(Duration.ofMillis(10));
        AtomicInteger replayChecks = new AtomicInteger();
        CareerSingleFlightService service = new CareerSingleFlightService() {
            @Override
            public AcquireResult tryAcquire(String scene, String singleFlightKey, String ownerId, String traceId) {
                return new AcquireResult(false, false, CareerSingleFlightRecordDO.builder()
                        .singleFlightKey(singleFlightKey)
                        .scene(scene)
                        .ownerId("owner-running")
                        .fencingToken(1L)
                        .status("RUNNING")
                        .requestCount(2)
                        .heartbeatTime(new Date())
                        .build());
            }

            @Override
            public boolean heartbeat(String singleFlightKey, String ownerId, long fencingToken) {
                return false;
            }

            @Override
            public boolean completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson) {
                return false;
            }

            @Override
            public boolean completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType) {
                return false;
            }

            @Override
            public Optional<CareerSingleFlightRecordDO> replayIfAvailable(String singleFlightKey) {
                if (replayChecks.incrementAndGet() < 3) {
                    return Optional.empty();
                }
                return Optional.of(CareerSingleFlightRecordDO.builder()
                        .singleFlightKey(singleFlightKey)
                        .scene("INTERVIEW_EVALUATE")
                        .ownerId("owner-running")
                        .fencingToken(1L)
                        .status("SUCCESS")
                        .resultJson("{\"response\":\"owner-result\"}")
                        .build());
            }
        };
        CareerSingleFlightLlmServiceImpl wrapper = new CareerSingleFlightLlmServiceImpl(
                service,
                llmService,
                new CareerTaskAttemptRecorder(attemptMapper),
                newGuardService(),
                properties);

        String result = wrapper.chat("INTERVIEW_EVALUATE", "manual", "trace-follower",
                ChatRequest.builder().messages(List.of(ChatMessage.user("prompt"))).build());

        assertEquals("owner-result", result);
        verify(llmService, never()).chat(any(ChatRequest.class));
        assertEquals(1, attempts.size());
        assertEquals("REPLAYED", attempts.get(0).getStatus());
        assertTrue(attempts.get(0).getReplayed());
    }

    /**
     * 验证 follower 等待期间可以从本地 L1 读取 owner 成功回放。
     */
    /**
     * 验证模型空响应在首次调用和回放调用中保持一致的 null 语义。
     */
    @Test
    void llmWrapperFollowerWaitReadsLocalReplayCache() {
        stubAttemptPersistence();
        CareerSingleFlightProperties properties = newTestSingleFlightProperties();
        CareerSingleFlightLocalReplayCache localReplayCache = new CareerSingleFlightLocalReplayCache();
        CareerSingleFlightHeartbeatManager heartbeatManager = new CareerSingleFlightHeartbeatManager();
        String scene = "INTERVIEW_EVALUATE";
        String rawKey = "local-follower";
        CareerSingleFlightService service = new CareerSingleFlightService() {
            @Override
            public AcquireResult tryAcquire(String scene, String singleFlightKey, String ownerId, String traceId) {
                localReplayCache.putSuccess(singleFlightKey, CareerSingleFlightRecordDO.builder()
                        .singleFlightKey(singleFlightKey)
                        .scene(scene)
                        .ownerId("owner-running")
                        .fencingToken(1L)
                        .status("SUCCESS")
                        .resultJson("{\"response\":\"cached-owner-result\"}")
                        .build(), properties.localReplayTtlMillis());
                return new AcquireResult(false, false, CareerSingleFlightRecordDO.builder()
                        .singleFlightKey(singleFlightKey)
                        .scene(scene)
                        .ownerId("owner-running")
                        .fencingToken(1L)
                        .status("RUNNING")
                        .heartbeatTime(new Date())
                        .build());
            }

            @Override
            public boolean heartbeat(String singleFlightKey, String ownerId, long fencingToken) {
                return false;
            }

            @Override
            public boolean completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson) {
                return false;
            }

            @Override
            public boolean completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType) {
                return false;
            }

            @Override
            public Optional<CareerSingleFlightRecordDO> replayIfAvailable(String singleFlightKey) {
                return Optional.empty();
            }
        };
        CareerSingleFlightLlmServiceImpl wrapper = new CareerSingleFlightLlmServiceImpl(
                service,
                llmService,
                new CareerTaskAttemptRecorder(attemptMapper),
                newGuardService(),
                properties,
                localReplayCache,
                heartbeatManager);
        try {
            String result = wrapper.chat(scene, rawKey, "trace-follower-local",
                    ChatRequest.builder().messages(List.of(ChatMessage.user("prompt"))).build());

            assertEquals("cached-owner-result", result);
            verify(llmService, never()).chat(any(ChatRequest.class));
            assertEquals(1, attempts.size());
            assertEquals("REPLAYED", attempts.get(0).getStatus());
        } finally {
            heartbeatManager.shutdown();
        }
    }

    @Test
    void llmWrapperRefreshesHeartbeatDuringLongOwnerCall() {
        stubAttemptPersistence();
        CareerSingleFlightProperties properties = newTestSingleFlightProperties();
        properties.setHeartbeatInterval(Duration.ofMillis(10));
        CareerSingleFlightLocalReplayCache localReplayCache = new CareerSingleFlightLocalReplayCache();
        CareerSingleFlightHeartbeatManager heartbeatManager = new CareerSingleFlightHeartbeatManager();
        AtomicInteger heartbeatCount = new AtomicInteger();
        CountDownLatch heartbeatLatch = new CountDownLatch(3);
        CareerSingleFlightService service = new CareerSingleFlightService() {
            @Override
            public AcquireResult tryAcquire(String scene, String singleFlightKey, String ownerId, String traceId) {
                return new AcquireResult(true, false, CareerSingleFlightRecordDO.builder()
                        .singleFlightKey(singleFlightKey)
                        .scene(scene)
                        .ownerId(ownerId)
                        .fencingToken(1L)
                        .status("RUNNING")
                        .heartbeatTime(new Date())
                        .build());
            }

            @Override
            public boolean heartbeat(String singleFlightKey, String ownerId, long fencingToken) {
                heartbeatCount.incrementAndGet();
                heartbeatLatch.countDown();
                return true;
            }

            @Override
            public boolean completeSuccess(String singleFlightKey, String ownerId, long fencingToken, String resultJson) {
                return true;
            }

            @Override
            public boolean completeFailure(String singleFlightKey, String ownerId, long fencingToken, String errorType) {
                return true;
            }

            @Override
            public Optional<CareerSingleFlightRecordDO> replayIfAvailable(String singleFlightKey) {
                return Optional.empty();
            }
        };
        CareerSingleFlightLlmServiceImpl wrapper = new CareerSingleFlightLlmServiceImpl(
                service,
                llmService,
                new CareerTaskAttemptRecorder(attemptMapper),
                newGuardService(),
                properties,
                localReplayCache,
                heartbeatManager);
        doAnswer(invocation -> {
            assertTrue(await(heartbeatLatch, 500L));
            return "long-owner-result";
        }).when(llmService).chat(any(ChatRequest.class));
        try {
            String result = wrapper.chat("INTERVIEW_EVALUATE", "long-owner", "trace-heartbeat",
                    ChatRequest.builder().messages(List.of(ChatMessage.user("prompt"))).build());

            assertEquals("long-owner-result", result);
            assertTrue(heartbeatCount.get() >= 3);
            assertEquals(1, attempts.size());
            assertEquals("SUCCESS", attempts.get(0).getStatus());
        } finally {
            heartbeatManager.shutdown();
        }
    }

    @Test
    void llmWrapperReplaysNullModelResultAsNull() {
        stubPersistence();
        stubAttemptPersistence();
        CareerSingleFlightLlmServiceImpl wrapper = newWrapper();
        when(llmService.chat(any(ChatRequest.class))).thenReturn(null);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("null prompt")))
                .temperature(0.1D)
                .build();

        String first = wrapper.chat("OPTIMIZATION_REVIEW", "null-key", "trace-1", request);
        String replay = wrapper.chat("OPTIMIZATION_REVIEW", "null-key", "trace-2", request);

        assertNull(first);
        assertNull(replay);
        verify(llmService).chat(any(ChatRequest.class));
        assertTrue(records.get(0).getResultJson().contains("\"response\":null"));
    }

    /**
     * 验证瞬时模型异常会被守卫重试，single-flight 只完成一次成功。
     */
    @Test
    void llmWrapperRetriesTransientFailureAndCompletesSingleFlightOnce() {
        stubPersistence();
        stubAttemptPersistence();
        CareerAiGuardProperties properties = newTestGuardProperties();
        properties.getStages().get("OPTIMIZATION_EXECUTOR").setRetryMaxAttempts(2);
        CareerSingleFlightLlmServiceImpl wrapper = newWrapper(new CareerAiGuardService(properties));
        AtomicInteger calls = new AtomicInteger();
        when(llmService.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new ServiceException("transient model error");
            }
            return "model-after-retry";
        });

        String result = wrapper.chat("OPTIMIZATION_EXECUTOR", "retry-key", "trace-retry", ChatRequest.builder()
                .messages(List.of(ChatMessage.user("prompt")))
                .build());

        assertEquals("model-after-retry", result);
        assertEquals(2, calls.get());
        assertEquals(1, records.size());
        assertEquals("SUCCESS", records.get(0).getStatus());
        assertEquals(1, attempts.size());
        assertEquals("SUCCESS", attempts.get(0).getStatus());
        verify(recordMapper, times(2)).updateById(any(CareerSingleFlightRecordDO.class));
    }

    /**
     * 验证非 owner 的防重路径不会进入模型调用，也不会触发守卫重试。
     */
    @Test
    void llmWrapperRejectsDuplicateWhileOwnerStillRunning() {
        stubPersistence();
        CareerSingleFlightServiceImpl service = newService();
        service.tryAcquire("INTERVIEW_EVALUATE", stableWrapperKey("INTERVIEW_EVALUATE", "manual"), "owner-a", "trace-1");
        stubAttemptPersistence();
        CareerSingleFlightLlmServiceImpl wrapper = new CareerSingleFlightLlmServiceImpl(
                service, llmService, new CareerTaskAttemptRecorder(attemptMapper), newGuardService());

        assertThrows(ServiceException.class, () -> wrapper.chat("INTERVIEW_EVALUATE", "manual", "trace-2",
                ChatRequest.builder().messages(List.of(ChatMessage.user("prompt"))).build()));
        verify(llmService, never()).chat(any(ChatRequest.class));
        assertEquals(1, attempts.size());
        assertEquals("FAILED", attempts.get(0).getStatus());
        assertEquals("ServiceException", attempts.get(0).getErrorType());
    }

    /**
     * 验证守卫超时会转换成可识别异常，并记录 failed attempt。
     */
    @Test
    void llmWrapperRecordsTimeoutAsFailedAttempt() {
        stubPersistence();
        stubAttemptPersistence();
        CareerAiGuardProperties properties = newTestGuardProperties();
        properties.getStages().get("INTERVIEW_REPORT").setTimeout(Duration.ofMillis(30));
        properties.getStages().get("INTERVIEW_REPORT").setRetryMaxAttempts(1);
        CareerSingleFlightLlmServiceImpl wrapper = newWrapper(new CareerAiGuardService(properties));
        doAnswer(invocation -> {
            sleep(200L);
            return "late-result";
        }).when(llmService).chat(any(ChatRequest.class));

        assertThrows(CareerAiGuardTimeoutException.class, () -> wrapper.chat("INTERVIEW_REPORT",
                "INTERVIEW_REPORT:user-1:session-1:turns",
                "trace-timeout",
                ChatRequest.builder().messages(List.of(ChatMessage.user("report prompt"))).build()));

        assertEquals(1, attempts.size());
        CareerTaskAttemptDO attempt = attempts.get(0);
        assertEquals("FAILED", attempt.getStatus());
        assertEquals("CareerAiGuardTimeoutException", attempt.getErrorType());
        assertTrue(attempt.getErrorMessage().contains("Career AI guard timeout"));
        assertEquals("FAILED", records.get(0).getStatus());
        verify(llmService, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void llmWrapperRecordsAttemptMetadataForArtifactCreatingAiCall() {
        stubPersistence();
        stubAttemptPersistence();
        CareerSingleFlightLlmServiceImpl wrapper = newWrapper();
        when(llmService.chat(any(ChatRequest.class))).thenReturn("{\"ok\":true}");
        String rawKey = "OPTIMIZATION_EXECUTOR:user-1:task-1:prompt-body";

        wrapper.chat("OPTIMIZATION_EXECUTOR", rawKey, "trace-task-1", ChatRequest.builder()
                .messages(List.of(ChatMessage.user("prompt-body with resume and JD evidence")))
                .temperature(0.1D)
                .build());

        assertEquals(1, attempts.size());
        CareerTaskAttemptDO attempt = attempts.get(0);
        assertEquals("OPTIMIZATION_EXECUTOR", attempt.getScene());
        assertEquals("user-1", attempt.getUserId());
        assertEquals("task-1", attempt.getBusinessId());
        assertEquals(rawKey, attempt.getIdempotencyKey());
        assertEquals(stableWrapperKey("OPTIMIZATION_EXECUTOR", rawKey), attempt.getSingleFlightKey());
        assertEquals("trace-task-1", attempt.getTraceId());
        assertEquals("RagentModelRouter", attempt.getModelName());
        assertEquals("SUCCESS", attempt.getStatus());
        assertTrue(attempt.getPromptSummary().contains("messages=1"));
        assertTrue(attempt.getPromptSummary().contains("sha256="));
        assertFalse(attempt.getPromptSummary().contains("resume and JD evidence"));
        assertTrue(attempt.getLatencyMs() >= 0);
    }

    @Test
    void llmWrapperRecordsFailedAttemptWithErrorType() {
        stubPersistence();
        stubAttemptPersistence();
        CareerSingleFlightLlmServiceImpl wrapper = newWrapper();
        when(llmService.chat(any(ChatRequest.class))).thenThrow(new ServiceException("model timeout"));

        assertThrows(ServiceException.class, () -> wrapper.chat("INTERVIEW_REPORT",
                "INTERVIEW_REPORT:user-1:session-1:turns",
                "trace-report-1",
                ChatRequest.builder().messages(List.of(ChatMessage.user("report prompt"))).build()));

        assertEquals(1, attempts.size());
        CareerTaskAttemptDO attempt = attempts.get(0);
        assertEquals("FAILED", attempt.getStatus());
        assertEquals("ServiceException", attempt.getErrorType());
        assertTrue(attempt.getErrorMessage().contains("model timeout"));
    }

    private CareerSingleFlightServiceImpl newService() {
        return new CareerSingleFlightServiceImpl(recordMapper);
    }

    /**
     * 创建默认 AI Guard 的 single-flight LLM 包装器。
     */
    private CareerSingleFlightLlmServiceImpl newWrapper() {
        return newWrapper(newGuardService());
    }

    /**
     * 创建带指定 AI Guard 的 single-flight LLM 包装器。
     */
    private CareerSingleFlightLlmServiceImpl newWrapper(CareerAiGuardService guardService) {
        return new CareerSingleFlightLlmServiceImpl(
                newService(),
                llmService,
                new CareerTaskAttemptRecorder(attemptMapper),
                guardService);
    }

    /**
     * 创建测试用 AI Guard。
     */
    private CareerAiGuardService newGuardService() {
        return new CareerAiGuardService(newTestGuardProperties());
    }

    /**
     * 创建测试用守卫配置，移除重试等待以加快单测。
     */
    private CareerAiGuardProperties newTestGuardProperties() {
        CareerAiGuardProperties properties = new CareerAiGuardProperties();
        properties.getDefaultPolicy().setRetryWaitDuration(Duration.ZERO);
        properties.getStages().values().forEach(policy -> policy.setRetryWaitDuration(Duration.ZERO));
        return properties;
    }

    /**
     * 创建测试用 single-flight 配置，缩短 follower 等待窗口。
     */
    private CareerSingleFlightProperties newTestSingleFlightProperties() {
        CareerSingleFlightProperties properties = new CareerSingleFlightProperties();
        properties.setEnabled(true);
        properties.setMode(CareerSingleFlightMode.HYBRID);
        properties.setOwnerTimeout(Duration.ofMillis(120_000));
        properties.setWaitTimeout(Duration.ofMillis(80));
        properties.setPollInterval(Duration.ofMillis(5));
        properties.setRedisTtl(Duration.ofMinutes(5));
        return properties;
    }

    /**
     * 构造 Redis 协调器返回的状态快照。
     */
    private CareerSingleFlightRedisCoordinator.RedisState redisState(boolean owner,
                                                                    boolean replayAvailable,
                                                                    String scene,
                                                                    String key,
                                                                    String ownerId,
                                                                    long fencingToken,
                                                                    String status,
                                                                    String resultJson,
                                                                    String errorType,
                                                                    int requestCount) {
        return new CareerSingleFlightRedisCoordinator.RedisState(owner,
                replayAvailable,
                scene,
                key,
                ownerId,
                fencingToken,
                status,
                System.currentTimeMillis(),
                requestCount,
                resultJson,
                errorType,
                null);
    }

    /**
     * 测试中使用的短暂阻塞。
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("sleep interrupted");
        }
    }

    /**
     * 等待异步条件达成，避免单测依赖固定睡眠。
     */
    private boolean await(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("await interrupted");
        }
    }

    private String stableWrapperKey(String scene, String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest((scene + ":" + rawKey).getBytes(StandardCharsets.UTF_8)));
            return scene + ":" + hash;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void stubPersistence() {
        records.clear();
        lenient().when(recordMapper.selectList(anyRecordWrapper())).thenAnswer(invocation -> List.copyOf(records));
        doAnswer(invocation -> {
            CareerSingleFlightRecordDO record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId("sf-" + (records.size() + 1));
            }
            records.add(record);
            return 1;
        }).when(recordMapper).insert(any(CareerSingleFlightRecordDO.class));
        lenient().doAnswer(invocation -> 1).when(recordMapper).updateById(any(CareerSingleFlightRecordDO.class));
    }

    private void stubAttemptPersistence() {
        attempts.clear();
        lenient().doAnswer(invocation -> {
            CareerTaskAttemptDO attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId("attempt-" + (attempts.size() + 1));
            }
            attempts.add(attempt);
            return 1;
        }).when(attemptMapper).insert(any(CareerTaskAttemptDO.class));
        lenient().doAnswer(invocation -> {
            CareerTaskAttemptDO attempt = invocation.getArgument(0);
            attempts.replaceAll(existing -> existing.getId().equals(attempt.getId()) ? attempt : existing);
            return 1;
        }).when(attemptMapper).updateById(any(CareerTaskAttemptDO.class));
    }

    @SuppressWarnings("unchecked")
    private Wrapper<CareerSingleFlightRecordDO> anyRecordWrapper() {
        return (Wrapper<CareerSingleFlightRecordDO>) any(Wrapper.class);
    }
}
