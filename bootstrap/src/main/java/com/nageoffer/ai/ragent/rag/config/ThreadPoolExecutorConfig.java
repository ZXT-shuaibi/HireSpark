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

package com.nageoffer.ai.ragent.rag.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.nageoffer.ai.ragent.framework.threadpool.AdaptiveBufferedThreadPoolExecutor;
import com.nageoffer.ai.ragent.framework.threadpool.RefreshableExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池执行器配置类。
 *
 * <p>三个 IO 密集型线程池（modelStream / ragRetrieval / ragContext）以
 * {@link RefreshableExecutor} 暴露，初始为 Adaptive 实现。
 * 修改 {@code ragent.threadpool.mode} 配置后重启即可切换实现。
 *
 * <pre>
 *   # Nacos / Apollo / application.yml
 *   ragent:
 *     threadpool:
 *       mode: adaptive        # 默认（自适应缓冲线程池）
 *       # mode: jdk-legacy   # 切回 JDK 原生
 * </pre>
 */
@Configuration
public class ThreadPoolExecutorConfig {

    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    // ==================== IO 密集型（RefreshableExecutor 包装） ====================

    @Bean
    public Executor modelStreamExecutor() {
        return new RefreshableExecutor(new AdaptiveBufferedThreadPoolExecutor(
                Math.max(2, CPU_COUNT >> 1), CPU_COUNT * 4,
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create().setNamePrefix("model_stream_executor_").build(),
                new AdaptiveBufferedThreadPoolExecutor.CountPolicy(),
                0.7, true, 8, 0.7, 10, 200, 5));
    }

    @Bean
    public Executor ragRetrievalExecutor() {
        return new RefreshableExecutor(new AdaptiveBufferedThreadPoolExecutor(
                CPU_COUNT, CPU_COUNT * 3,
                60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                ThreadFactoryBuilder.create().setNamePrefix("rag_retrieval_executor_").build(),
                new AdaptiveBufferedThreadPoolExecutor.CountPolicy(),
                0.6, true, 8, 0.7, 10, 200, 5));
    }

    @Bean
    public Executor ragContextExecutor() {
        return new RefreshableExecutor(new AdaptiveBufferedThreadPoolExecutor(
                CPU_COUNT, CPU_COUNT * 3,
                60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                ThreadFactoryBuilder.create().setNamePrefix("rag_context_executor_").build(),
                new AdaptiveBufferedThreadPoolExecutor.CountPolicy(),
                0.6, true, 8, 0.7, 10, 200, 5));
    }

    // ==================== 共用 ====================

    @Bean
    public Executor mcpBatchExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CPU_COUNT, CPU_COUNT << 1, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ThreadFactoryBuilder.create().setNamePrefix("mcp_batch_executor_").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Bean
    public Executor innerRetrievalExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CPU_COUNT << 1, CPU_COUNT << 2, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                ThreadFactoryBuilder.create().setNamePrefix("rag_inner_retrieval_executor_").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Bean
    public Executor intentClassifyExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CPU_COUNT, CPU_COUNT << 1, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ThreadFactoryBuilder.create().setNamePrefix("intent_classify_executor_").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Bean
    public Executor memorySummaryExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, Math.max(2, CPU_COUNT >> 1), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create().setNamePrefix("memory_summary_executor_").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Bean
    public Executor chatEntryExecutor(RAGRateLimitProperties rateLimitProperties) {
        int size = rateLimitProperties.getGlobalMaxConcurrent();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size, size, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ThreadFactoryBuilder.create().setNamePrefix("chat_entry_executor_").build(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Bean
    public Executor knowledgeChunkExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT >> 1), Math.max(4, CPU_COUNT), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create().setNamePrefix("kb_chunk_executor_").build(),
                new ThreadPoolExecutor.AbortPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }

    @Bean
    public Executor memoryLoadExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT >> 1), Math.max(4, CPU_COUNT), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create().setNamePrefix("memory_load_executor_").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutor(executor);
    }
}
