package com.nageoffer.ai.ragent.rag.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.nageoffer.ai.ragent.framework.threadpool.AdaptiveBufferedThreadPoolExecutor;
import com.nageoffer.ai.ragent.framework.threadpool.AdaptiveRejectedHandler;

import java.text.DecimalFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应线程池 vs JDK 原生线程池性能对比测试。
 *
 * <p>模拟 Ragent 生产场景：IO 密集型（LLM API 调用，50ms 延迟），
 * 10 轮 × 200 任务的突发提交，每轮间隔 100ms。
 *
 * <p>直接运行 main 方法查看对比结果。
 */
public class ThreadPoolBenchmark {

    private static final int CORE = 4;
    private static final int MAX = 8;
    private static final int QUEUE_SIZE = 200;
    private static final int ROUNDS = 10;
    private static final int TASKS_PER_ROUND = 200;
    private static final long ROUND_INTERVAL_MS = 100;
    private static final long IO_SLEEP_MS = 50; // 模拟 LLM 调用延迟

    private static final DecimalFormat PCT = new DecimalFormat("0.0%");

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(70));
        System.out.println("AdaptiveBufferedThreadPoolExecutor vs JDK ThreadPoolExecutor 性能对比");
        System.out.println("场景: IO密集 (50ms延迟) | core=" + CORE + " max=" + MAX
                + " queue=" + QUEUE_SIZE + " | " + ROUNDS + "轮×" + TASKS_PER_ROUND + "任务");
        System.out.println("=".repeat(70));

        // 预热
        warmup();

        // 场景1: 平稳负载（不 sleep，连续提交）
        System.out.println("\n--- 场景1: 连续突发（生产高峰期） ---");
        Result jdk1 = runJdk();
        Result adp1 = runAdaptive();

        printComparison(jdk1, adp1);

        Thread.sleep(2000);

        // 场景2: 间隔负载（每轮间有喘息）
        System.out.println("\n--- 场景2: 间隔负载（有间隔缓冲） ---");
        Result jdk2 = runJdkWithInterval();
        Result adp2 = runAdaptiveWithInterval();

        printComparison(jdk2, adp2);
    }

    // ==================== 场景1：连续突发 ====================

    static Result runJdk() {
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger completed = new AtomicInteger(0);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                ThreadFactoryBuilder.create().setNamePrefix("jdk_bench_").build(),
                (r, e) -> rejected.incrementAndGet()
        );

        long start = System.currentTimeMillis();
        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < TASKS_PER_ROUND; i++) {
                pool.execute(() -> {
                    long t0 = System.currentTimeMillis();
                    sleepMs(IO_SLEEP_MS);
                    totalLatency.addAndGet(System.currentTimeMillis() - t0);
                    completed.incrementAndGet();
                });
            }
        }
        pool.shutdown();
        try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        long elapsed = System.currentTimeMillis() - start;
        return new Result("JDK原生", rejected.get(), completed.get(), elapsed, totalLatency.get(), pool.getLargestPoolSize());
    }

    static Result runAdaptive() {
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger completed = new AtomicInteger(0);

        AdaptiveBufferedThreadPoolExecutor pool = new AdaptiveBufferedThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                ThreadFactoryBuilder.create().setNamePrefix("adp_bench_").build(),
                (r, e) -> rejected.incrementAndGet(),
                0.7, true, 8, 0.7, 10, 200, 5
        );

        long start = System.currentTimeMillis();
        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < TASKS_PER_ROUND; i++) {
                pool.execute(() -> {
                    long t0 = System.currentTimeMillis();
                    sleepMs(IO_SLEEP_MS);
                    totalLatency.addAndGet(System.currentTimeMillis() - t0);
                    completed.incrementAndGet();
                });
            }
        }
        pool.shutdown();
        try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        long elapsed = System.currentTimeMillis() - start;
        return new Result("Adaptive", rejected.get(), completed.get(), elapsed, totalLatency.get(), pool.getLargestPoolSize());
    }

    // ==================== 场景2：间隔负载 ====================

    static Result runJdkWithInterval() {
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger completed = new AtomicInteger(0);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                ThreadFactoryBuilder.create().setNamePrefix("jdk_bench_").build(),
                (r, e) -> rejected.incrementAndGet()
        );

        long start = System.currentTimeMillis();
        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < TASKS_PER_ROUND; i++) {
                pool.execute(() -> {
                    long t0 = System.currentTimeMillis();
                    sleepMs(IO_SLEEP_MS);
                    totalLatency.addAndGet(System.currentTimeMillis() - t0);
                    completed.incrementAndGet();
                });
            }
            sleepMs(ROUND_INTERVAL_MS);
        }
        pool.shutdown();
        try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        long elapsed = System.currentTimeMillis() - start;
        return new Result("JDK原生", rejected.get(), completed.get(), elapsed, totalLatency.get(), pool.getLargestPoolSize());
    }

    static Result runAdaptiveWithInterval() {
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger completed = new AtomicInteger(0);

        AdaptiveBufferedThreadPoolExecutor pool = new AdaptiveBufferedThreadPoolExecutor(
                CORE, MAX, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),
                ThreadFactoryBuilder.create().setNamePrefix("adp_bench_").build(),
                (r, e) -> rejected.incrementAndGet(),
                0.7, true, 8, 0.7, 10, 200, 5
        );

        long start = System.currentTimeMillis();
        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < TASKS_PER_ROUND; i++) {
                pool.execute(() -> {
                    long t0 = System.currentTimeMillis();
                    sleepMs(IO_SLEEP_MS);
                    totalLatency.addAndGet(System.currentTimeMillis() - t0);
                    completed.incrementAndGet();
                });
            }
            sleepMs(ROUND_INTERVAL_MS);
        }
        pool.shutdown();
        try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        long elapsed = System.currentTimeMillis() - start;
        return new Result("Adaptive", rejected.get(), completed.get(), elapsed, totalLatency.get(), pool.getLargestPoolSize());
    }

    // ==================== 输出 ====================

    static void printComparison(Result jdk, Result adp) {
        System.out.println();
        System.out.printf("%-20s %12s %12s %12s%n", "指标", "JDK原生", "Adaptive", "改善");
        System.out.println("-".repeat(60));
        printRow("总耗时(ms)", jdk.elapsedMs, adp.elapsedMs, false);
        printRow("完成任务数", jdk.completed, adp.completed, true);
        printRow("拒绝任务数", jdk.rejected, adp.rejected, false);
        printRow("拒绝率", jdk.rejectRate(), adp.rejectRate(), false);
        printRow("最大线程数", jdk.largestPoolSize, adp.largestPoolSize, true);
        printRow("平均延迟(ms)", jdk.avgLatency(), adp.avgLatency(), false);
        printRow("吞吐量(task/s)", jdk.throughput(), adp.throughput(), true);
    }

    static void printRow(String label, double jdkVal, double adpVal, boolean higherBetter) {
        double diff = adpVal - jdkVal;
        double pctChange = jdkVal == 0 ? 0 : diff / jdkVal;
        boolean isImprovement = higherBetter ? diff > 0 : diff < 0;
        String arrow = isImprovement ? "↑" : "↓";
        System.out.printf("%-20s %12.0f %12.0f %12s (%+.0f%%)%n",
                label, jdkVal, adpVal,
                arrow, pctChange * 100);
    }

    // ==================== 辅助 ====================

    static void warmup() throws InterruptedException {
        ExecutorService warmup = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 100; i++) {
            warmup.execute(() -> sleepMs(10));
        }
        warmup.shutdown();
        warmup.awaitTermination(10, TimeUnit.SECONDS);
        Thread.sleep(500);
    }

    static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record Result(String name, int rejected, int completed, long elapsedMs, long totalLatencyMs, int largestPoolSize) {
        double rejectRate() { return completed + rejected == 0 ? 0 : (double) rejected / (completed + rejected); }
        double avgLatency() { return completed == 0 ? 0 : (double) totalLatencyMs / completed; }
        double throughput() { return elapsedMs == 0 ? 0 : (double) completed / elapsedMs * 1000; }
    }
}
