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

package com.nageoffer.ai.ragent.framework.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可热替换的 Executor 包装器。
 *
 * <p>对外暴露稳定的 {@link Executor} 引用，内部 delegate 可通过
 * {@link #swap(Executor)} 在运行时替换。旧 delegate 如果是
 * {@link ExecutorService} 则优雅关闭。
 *
 * <p>配合配置中心使用：配置变更时重建新池并通过 {@code swap()} 切入，
 * 所有已注入此包装器的消费者无需重新注入。
 */
public class RefreshableExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(RefreshableExecutor.class);

    private final AtomicReference<Executor> delegate;

    public RefreshableExecutor(Executor initial) {
        this.delegate = new AtomicReference<>(initial);
    }

    @Override
    public void execute(Runnable command) {
        delegate.get().execute(command);
    }

    /**
     * 热替换底层 Executor，旧池优雅关闭。
     */
    public void swap(Executor newExecutor) {
        Executor old = delegate.getAndSet(newExecutor);
        if (old instanceof ExecutorService es && old != newExecutor) {
            shutdownQuietly(es);
        }
        log.info("RefreshableExecutor swapped: {} -> {}",
                old.getClass().getSimpleName(), newExecutor.getClass().getSimpleName());
    }

    /**
     * 返回当前底层 Executor，供监控/测试使用。
     */
    public Executor getDelegate() {
        return delegate.get();
    }

    /**
     * 关闭当前持有的 ExecutorService（如果存在）。
     */
    public void shutdown() {
        Executor current = delegate.get();
        if (current instanceof ExecutorService es) {
            shutdownQuietly(es);
        }
    }

    private void shutdownQuietly(ExecutorService es) {
        es.shutdown();
        try {
            if (!es.awaitTermination(30, TimeUnit.SECONDS)) {
                es.shutdownNow();
            }
        } catch (InterruptedException e) {
            es.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
