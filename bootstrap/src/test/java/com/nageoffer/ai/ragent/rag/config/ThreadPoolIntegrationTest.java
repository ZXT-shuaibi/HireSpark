package com.nageoffer.ai.ragent.rag.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.DecimalFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring 集成测试：直接压测 {@link ThreadPoolExecutorConfig} 注入的生产线程池 Bean。
 *
 * <p>并发数 = 核心线程 × 4 的 IO 密集型模拟负载，统计完成率、平均延迟和拒绝数。
 *
 * <p>运行时可通过 {@code -Djdk.legacy=true} 切换回 JDK 原生池对比。
 */
@SpringBootTest
public class ThreadPoolIntegrationTest {

    @Autowired
    @Qualifier("modelStreamExecutor")
    private Executor modelStreamExecutor;

    @Autowired
    @Qualifier("ragRetrievalExecutor")
    private Executor ragRetrievalExecutor;

    @Autowired
    @Qualifier("ragContextExecutor")
    private Executor ragContextExecutor;

    private static final int CONCURRENT = Runtime.getRuntime().availableProcessors() * 4;
    private static final int TASKS_PER_THREAD = 50;
    private static final long IO_SLEEP_MS = 50;

    private static final DecimalFormat PCT = new DecimalFormat("0.0%");

    //    @Test
    public void benchmarkModelStream() throws InterruptedException {
        System.out.println("=== modelStreamExecutor (LLM 流式调用) ===");
        bench("modelStream", modelStreamExecutor);
    }

    //    @Test
    public void benchmarkRagRetrieval() throws InterruptedException {
        System.out.println("=== ragRetrievalExecutor (向量检索) ===");
        bench("ragRetrieval", ragRetrievalExecutor);
    }

    //    @Test
    public void benchmarkRagContext() throws InterruptedException {
        System.out.println("=== ragContextExecutor (RAG 上下文组装) ===");
        bench("ragContext", ragContextExecutor);
    }

    private void bench(String name, Executor executor) throws InterruptedException {
        int total = CONCURRENT * TASKS_PER_THREAD;
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT);

        long start = System.currentTimeMillis();
        for (int i = 0; i < CONCURRENT; i++) {
            new Thread(() -> {
                for (int j = 0; j < TASKS_PER_THREAD; j++) {
                    try {
                        executor.execute(() -> {
                            long t0 = System.currentTimeMillis();
                            sleepMs(IO_SLEEP_MS);
                            totalLatency.addAndGet(System.currentTimeMillis() - t0);
                            completed.incrementAndGet();
                        });
                    } catch (Exception e) {
                        rejected.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("  并发线程: %d | 总任务: %d | 完成: %d | 拒绝: %d (%.1f%%)%n",
                CONCURRENT, total, completed.get(), rejected.get(),
                (completed.get() + rejected.get() == 0 ? 0
                        : 100.0 * rejected.get() / (completed.get() + rejected.get())));
        System.out.printf("  总耗时: %dms | 平均延迟: %.1fms | 吞吐: %.0f task/s%n",
                elapsed,
                completed.get() == 0 ? 0 : (double) totalLatency.get() / completed.get(),
                elapsed == 0 ? 0 : (double) completed.get() / elapsed * 1000);
    }

    static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
