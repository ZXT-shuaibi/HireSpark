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

import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardProperties;
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardService;
import com.nageoffer.ai.ragent.career.service.guard.CareerAiGuardTimeoutException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerAiGuardServiceTest {

    /**
     * 验证同一守卫能按场景取得特定超时配置，未知场景使用默认配置。
     */
    @Test
    void stageSpecificTimeoutOverridesDefaultPolicy() {
        CareerAiGuardProperties properties = newTestGuardProperties();
        properties.getDefaultPolicy().setTimeout(Duration.ofMillis(300));
        properties.getStages().get("INTERVIEW_EVALUATE").setTimeout(Duration.ofMillis(30));
        properties.getStages().get("INTERVIEW_EVALUATE").setRetryMaxAttempts(1);
        CareerAiGuardService guardService = new CareerAiGuardService(properties);
        try {
            assertEquals("default-ok", guardService.execute("UNKNOWN_SCENE", () -> sleepAndReturn(60L, "default-ok")));
            assertThrows(CareerAiGuardTimeoutException.class,
                    () -> guardService.execute("INTERVIEW_EVALUATE", () -> sleepAndReturn(120L, "late")));
        } finally {
            guardService.shutdown();
        }
    }

    /**
     * 验证已知场景只覆盖显式字段，其余字段继续继承默认策略。
     */
    @Test
    void knownStageInheritsDefaultPolicyUnlessFieldIsOverridden() {
        CareerAiGuardProperties properties = new CareerAiGuardProperties();
        properties.getDefaultPolicy().setRetryMaxAttempts(2);
        properties.getDefaultPolicy().setTimeout(Duration.ofMillis(300));

        CareerAiGuardProperties.StagePolicy parsePolicy = properties.resolvePolicy("JD_PARSE");
        CareerAiGuardProperties.StagePolicy evaluatePolicy = properties.resolvePolicy("INTERVIEW_EVALUATE");

        assertEquals(2, parsePolicy.getRetryMaxAttempts());
        assertEquals(Duration.ofMillis(300), parsePolicy.getTimeout());
        assertEquals(2, evaluatePolicy.getRetryMaxAttempts());
        assertEquals(Duration.ofSeconds(8), evaluatePolicy.getTimeout());
    }

    /**
     * 验证配置中的小写和中划线场景键也能命中对应策略。
     */
    @Test
    void kebabCaseStageKeyMatchesNormalizedScene() {
        CareerAiGuardProperties properties = new CareerAiGuardProperties();
        CareerAiGuardProperties.StagePolicy override = new CareerAiGuardProperties.StagePolicy();
        override.setRetryMaxAttempts(1);
        override.setTimeout(Duration.ofMillis(40));
        properties.setStages(Map.of("interview-evaluate", override));

        CareerAiGuardProperties.StagePolicy policy = properties.resolvePolicy("INTERVIEW_EVALUATE");

        assertEquals(1, policy.getRetryMaxAttempts());
        assertEquals(Duration.ofMillis(40), policy.getTimeout());
        assertEquals(20, policy.getBulkheadMaxConcurrentCalls());
    }

    /**
     * 验证重试策略会重试瞬时异常并最终返回成功结果。
     */
    @Test
    void retryPolicyRetriesTransientException() {
        CareerAiGuardProperties properties = newTestGuardProperties();
        properties.getStages().get("JD_PARSE").setRetryMaxAttempts(2);
        CareerAiGuardService guardService = new CareerAiGuardService(properties);
        AtomicInteger calls = new AtomicInteger();
        try {
            String result = guardService.execute("JD_PARSE", () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new ServiceException("temporary model failure");
                }
                return "retry-ok";
            });

            assertEquals("retry-ok", result);
            assertEquals(2, calls.get());
        } finally {
            guardService.shutdown();
        }
    }

    /**
     * 验证 bulkhead 满载时会拒绝后续请求，且不会进入底层调用。
     */
    @Test
    void bulkheadRejectsWhenConcurrentLimitIsReached() throws Exception {
        CareerAiGuardProperties properties = newTestGuardProperties();
        properties.getStages().get("JD_ALIGNMENT").setBulkheadMaxConcurrentCalls(1);
        properties.getStages().get("JD_ALIGNMENT").setRetryMaxAttempts(1);
        CareerAiGuardService guardService = new CareerAiGuardService(properties);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger rejectedCalls = new AtomicInteger();
        Future<String> first = callerExecutor.submit(() -> guardService.execute("JD_ALIGNMENT", () -> {
            entered.countDown();
            await(release);
            return "first-ok";
        }));
        try {
            assertTrue(entered.await(500L, TimeUnit.MILLISECONDS));
            assertThrows(BulkheadFullException.class, () -> guardService.execute("JD_ALIGNMENT", () -> {
                rejectedCalls.incrementAndGet();
                return "second-ok";
            }));
            assertEquals(0, rejectedCalls.get());

            release.countDown();
            assertEquals("first-ok", first.get(500L, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            callerExecutor.shutdownNow();
            guardService.shutdown();
        }
    }

    /**
     * 创建测试用守卫配置，缩短等待时间以保证单测稳定快速。
     */
    private CareerAiGuardProperties newTestGuardProperties() {
        CareerAiGuardProperties properties = new CareerAiGuardProperties();
        properties.getDefaultPolicy().setRetryWaitDuration(Duration.ZERO);
        properties.getStages().values().forEach(policy -> policy.setRetryWaitDuration(Duration.ZERO));
        return properties;
    }

    /**
     * 测试中使用的可中断延迟返回。
     */
    private String sleepAndReturn(long millis, String value) {
        try {
            Thread.sleep(millis);
            return value;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("sleep interrupted");
        }
    }

    /**
     * 等待并在中断时转换为运行时异常。
     */
    private void await(CountDownLatch latch) {
        try {
            latch.await(1L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("await interrupted");
        }
    }
}
