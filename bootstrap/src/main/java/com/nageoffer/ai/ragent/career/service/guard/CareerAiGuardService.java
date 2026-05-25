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

package com.nageoffer.ai.ragent.career.service.guard;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.StrUtil;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CareerAiGuardService {

    private static final String DUPLICATE_RUNNING_MESSAGE = "AI request is already running for the same input";
    private static final int EXECUTOR_CORE_SIZE = 4;
    private static final int EXECUTOR_MAX_SIZE = 32;
    private static final int EXECUTOR_QUEUE_SIZE = 100;

    private final CareerAiGuardProperties properties;

    private final ExecutorService executorService = buildGuardExecutor();

    private final ConcurrentMap<String, GuardRuntime> runtimes = new ConcurrentHashMap<>();

    /**
     * 在指定场景的 AI Guard 下执行一次 Career AI 调用。
     */
    public <T> T execute(String scene, Supplier<T> supplier) {
        String normalizedScene = properties.normalize(scene);
        GuardRuntime runtime = runtimes.computeIfAbsent(normalizedScene, this::createRuntime);
        Supplier<T> timedSupplier = () -> executeWithTimeLimiter(normalizedScene, runtime, supplier);
        Supplier<T> retriedSupplier = Retry.decorateSupplier(runtime.retry(), timedSupplier);
        Supplier<T> bulkheadedSupplier = Bulkhead.decorateSupplier(runtime.bulkhead(), retriedSupplier);
        Supplier<T> circuitBrokenSupplier = CircuitBreaker.decorateSupplier(runtime.circuitBreaker(), bulkheadedSupplier);
        return circuitBrokenSupplier.get();
    }

    /**
     * 获取指定场景当前生效的守卫策略。
     */
    public CareerAiGuardProperties.StagePolicy policyFor(String scene) {
        return properties.resolvePolicy(scene);
    }

    /**
     * 关闭守卫异步执行线程池。
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * 创建指定场景的 Resilience4j 运行时组件。
     */
    private GuardRuntime createRuntime(String scene) {
        CareerAiGuardProperties.StagePolicy policy = properties.resolvePolicy(scene);
        CircuitBreaker circuitBreaker = CircuitBreaker.of("career-ai-" + scene, circuitBreakerConfig(policy));
        Bulkhead bulkhead = Bulkhead.of("career-ai-" + scene, bulkheadConfig(policy));
        Retry retry = Retry.of("career-ai-" + scene, retryConfig(policy));
        TimeLimiter timeLimiter = TimeLimiter.of(timeLimiterConfig(policy));
        return new GuardRuntime(circuitBreaker, bulkhead, retry, timeLimiter);
    }

    /**
     * 执行单次带超时限制的底层 AI 调用。
     */
    private <T> T executeWithTimeLimiter(String scene, GuardRuntime runtime, Supplier<T> supplier) {
        try {
            return runtime.timeLimiter().executeFutureSupplier(() -> executorService.submit(supplier::get));
        } catch (RuntimeException ex) {
            throw convertException(scene, ex);
        } catch (Exception ex) {
            throw convertException(scene, ex);
        }
    }

    /**
     * 构建断路器配置。
     */
    private CircuitBreakerConfig circuitBreakerConfig(CareerAiGuardProperties.StagePolicy policy) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(policy.getFailureRateThreshold())
                .slidingWindowSize(policy.getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(policy.getPermittedNumberOfCallsInHalfOpenState())
                .waitDurationInOpenState(policy.getWaitDurationInOpenState())
                .recordException(this::shouldRecordCircuitBreakerFailure)
                .build();
    }

    /**
     * 构建并发隔离配置。
     */
    private BulkheadConfig bulkheadConfig(CareerAiGuardProperties.StagePolicy policy) {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(policy.getBulkheadMaxConcurrentCalls())
                .maxWaitDuration(Duration.ZERO)
                .build();
    }

    /**
     * 构建重试配置。
     */
    private RetryConfig retryConfig(CareerAiGuardProperties.StagePolicy policy) {
        return RetryConfig.custom()
                .maxAttempts(policy.getRetryMaxAttempts())
                .waitDuration(policy.getRetryWaitDuration())
                .retryOnException(this::shouldRetry)
                .build();
    }

    /**
     * 构建超时配置。
     */
    private TimeLimiterConfig timeLimiterConfig(CareerAiGuardProperties.StagePolicy policy) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(policy.getTimeout())
                .cancelRunningFuture(true)
                .build();
    }

    /**
     * 判断异常是否应该触发重试。
     */
    private boolean shouldRetry(Throwable throwable) {
        Throwable actual = unwrap(throwable);
        return !(actual instanceof CareerAiGuardTimeoutException)
                && !(actual instanceof CallNotPermittedException)
                && !(actual instanceof BulkheadFullException)
                && !(actual instanceof RejectedExecutionException)
                && !isDuplicateRunningException(actual);
    }

    /**
     * 判断异常是否应该计入断路器失败率。
     */
    private boolean shouldRecordCircuitBreakerFailure(Throwable throwable) {
        return !isDuplicateRunningException(unwrap(throwable));
    }

    /**
     * 将守卫层异常转换为业务可识别的运行时异常。
     */
    private RuntimeException convertException(String scene, Throwable throwable) {
        Throwable actual = unwrap(throwable);
        if (actual instanceof CareerAiGuardTimeoutException timeoutException) {
            return timeoutException;
        }
        if (actual instanceof TimeoutException) {
            return new CareerAiGuardTimeoutException(scene, actual);
        }
        if (actual instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new ServiceException("Career AI guard failed for scene " + scene, actual, BaseErrorCode.SERVICE_ERROR);
    }

    /**
     * 展开异步执行包装异常，返回真实业务异常。
     */
    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 判断是否为 single-flight 非 owner 的防重异常。
     */
    private boolean isDuplicateRunningException(Throwable throwable) {
        return throwable instanceof ServiceException
                && StrUtil.equals(throwable.getMessage(), DUPLICATE_RUNNING_MESSAGE);
    }

    /**
     * 创建有界 TTL 守卫线程池，避免超时任务堆积时无限创建线程。
     */
    private static ExecutorService buildGuardExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                EXECUTOR_CORE_SIZE,
                EXECUTOR_MAX_SIZE,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(EXECUTOR_QUEUE_SIZE),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("career_ai_guard_")
                        .setDaemon(true)
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return TtlExecutors.getTtlExecutorService(executor);
    }

    private record GuardRuntime(CircuitBreaker circuitBreaker,
                                Bulkhead bulkhead,
                                Retry retry,
                                TimeLimiter timeLimiter) {
    }
}
