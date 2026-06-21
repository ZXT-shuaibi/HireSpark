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

package com.nageoffer.ai.ragent.rag.controller;

import com.nageoffer.ai.ragent.framework.threadpool.AdaptiveBufferedThreadPoolExecutor;
import com.nageoffer.ai.ragent.framework.threadpool.RefreshableExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池压测辅助端点（仅在测试环境启用）。
 *
 * <p>配合 JMeter / wrk 使用：
 * <pre>
 *   wrk -t4 -c32 -d60s http://localhost:9090/bench/io-task?delayMs=50
 * </pre>
 */
@RestController
public class ThreadPoolBenchController {

    private final Executor modelStreamExecutor;
    private final Executor ragRetrievalExecutor;
    private final Executor ragContextExecutor;

    private final AtomicLong rejectedModelStream = new AtomicLong(0);
    private final AtomicLong rejectedRagRetrieval = new AtomicLong(0);
    private final AtomicLong rejectedRagContext = new AtomicLong(0);

    public ThreadPoolBenchController(
            @Qualifier("modelStreamExecutor") Executor modelStreamExecutor,
            @Qualifier("ragRetrievalExecutor") Executor ragRetrievalExecutor,
            @Qualifier("ragContextExecutor") Executor ragContextExecutor) {
        this.modelStreamExecutor = modelStreamExecutor;
        this.ragRetrievalExecutor = ragRetrievalExecutor;
        this.ragContextExecutor = ragContextExecutor;
    }

    /**
     * 向 modelStreamExecutor 提交一个指定延迟的模拟 IO 任务。
     *
     * @param delayMs 模拟的 IO 延迟，默认 50ms
     */
    @GetMapping("/bench/io-task")
    public Map<String, Object> ioTask(int delayMs) {
        int delay = delayMs > 0 ? delayMs : 50;
        try {
            modelStreamExecutor.execute(() -> {
                try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        } catch (Exception e) {
            rejectedModelStream.incrementAndGet();
        }
        return metrics();
    }

    /**
     * 返回三个池的实时指标，供 Prometheus / 压测脚本轮询。
     */
    @GetMapping("/bench/metrics")
    public Map<String, Object> metrics() {
        return Map.of(
                "modelStream", poolMetrics(modelStreamExecutor, rejectedModelStream.get()),
                "ragRetrieval", poolMetrics(ragRetrievalExecutor, rejectedRagRetrieval.get()),
                "ragContext", poolMetrics(ragContextExecutor, rejectedRagContext.get()),
                "mode", detectMode()
        );
    }

    private Map<String, Object> poolMetrics(Executor executor, long rejected) {
        Executor actual = unwrap(executor);
        int poolSize = 0, active = 0, queueSize = 0, core = 0, max = 0;
        long completed = 0;
        if (actual instanceof AdaptiveBufferedThreadPoolExecutor adp) {
            poolSize = adp.getPoolSize();
            active = adp.getActiveCount();
            queueSize = adp.getQueue().size();
            core = adp.getCorePoolSize();
            max = adp.getMaximumPoolSize();
            completed = adp.getCompletedTaskCount();
        } else if (actual instanceof ThreadPoolExecutor tpe) {
            poolSize = tpe.getPoolSize();
            active = tpe.getActiveCount();
            queueSize = tpe.getQueue().size();
            core = tpe.getCorePoolSize();
            max = tpe.getMaximumPoolSize();
            completed = tpe.getCompletedTaskCount();
        }
        return Map.of("type", actual.getClass().getSimpleName(),
                "poolSize", poolSize, "active", active, "queueSize", queueSize,
                "core", core, "max", max, "completed", completed, "rejected", rejected);
    }

    private String detectMode() {
        Executor actual = unwrap(modelStreamExecutor);
        return actual instanceof AdaptiveBufferedThreadPoolExecutor ? "adaptive" : "jdk-legacy";
    }

    private Executor unwrap(Executor executor) {
        if (executor instanceof RefreshableExecutor re) {
            return re.getDelegate();
        }
        return executor;
    }
}
