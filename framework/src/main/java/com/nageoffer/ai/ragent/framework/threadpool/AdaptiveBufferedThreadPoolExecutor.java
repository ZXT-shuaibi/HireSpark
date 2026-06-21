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

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自适应缓冲线程池执行器。
 *
 * <p>在 JDK {@link ThreadPoolExecutor} 的核心逻辑基础上增加：
 * <ul>
 *     <li><b>缓冲度控制</b>：队列占用超过 {@code bufferDegree} 时跳过入队直接创建新线程，避免任务长期堆积。</li>
 *     <li><b>强制入队 + 动态策略</b>：线程数已满时根据 CPU 负载和线程池负载组合选择空转等待（指数退避）
 *         或阻塞等待，替代硬拒绝。</li>
 *     <li><b>TTL 上下文透传</b>：提交的任务由调用方负责通过 {@code TtlRunnable.get()} 包装。</li>
 *     <li><b>活跃线程计数</b>：通过 {@link #beforeExecute}/{@link #afterExecute} 维护实际并发度，
 *         替代手动维护的计数器。</li>
 * </ul>
 */
public class AdaptiveBufferedThreadPoolExecutor extends AbstractExecutorService {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveBufferedThreadPoolExecutor.class);

    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;

    private static final int RUNNING = -1 << COUNT_BITS;
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    private static final int STOP = 1 << COUNT_BITS;
    private static final int TIDYING = 2 << COUNT_BITS;
    private static final int TERMINATED = 3 << COUNT_BITS;

    private static int runStateOf(int c) { return c & ~CAPACITY; }
    private static int workerCountOf(int c) { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }
    private static boolean runStateLessThan(int c, int s) { return c < s; }
    private static boolean runStateAtLeast(int c, int s) { return c >= s; }
    private static boolean isRunning(int c) { return c < SHUTDOWN; }

    private final boolean preventRejection;

    private final BlockingQueue<Runnable> workQueue;
    private final ReentrantLock mainLock = new ReentrantLock();
    private final HashSet<Worker> workers = new HashSet<>();
    private final Condition termination = mainLock.newCondition();

    private int largestPoolSize;
    private long completedTaskCount;

    private volatile ThreadFactory threadFactory;
    private volatile AdaptiveRejectedHandler handler;
    private volatile long keepAliveTime;
    private volatile boolean allowCoreThreadTimeOut;
    private volatile int corePoolSize;
    private volatile int maximumPoolSize;

    // === 自适应参数 ===
    private final double bufferDegree;
    private final int threadLoadJudge;
    private final double cpuLoadJudge;
    private final int maxRetryAttempts;
    private volatile long spinWaitTimeNanos;
    private final long blockTimeoutMillis;

    // === 活跃线程计数：beforeExecute +1, afterExecute -1 ===
    private final AtomicInteger activeCount = new AtomicInteger(0);

    // === CPU 负载：1s 采样 + EMA 平滑 ===
    private final CpuLoadSampler cpuLoadSampler;

    /**
     * 全参数构造器。
     */
    public AdaptiveBufferedThreadPoolExecutor(int corePoolSize,
                                              int maximumPoolSize,
                                              long keepAliveTime,
                                              TimeUnit unit,
                                              BlockingQueue<Runnable> workQueue,
                                              ThreadFactory threadFactory,
                                              AdaptiveRejectedHandler handler,
                                              double bufferDegree,
                                              boolean preventRejection,
                                              int threadLoadJudge,
                                              double cpuLoadJudge,
                                              long spinWaitTimeMillis,
                                              long blockTimeoutMillis,
                                              int maxRetryAttempts) {
        if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }
        if (workQueue == null || threadFactory == null || handler == null) {
            throw new NullPointerException();
        }
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
        this.bufferDegree = bufferDegree;
        this.preventRejection = preventRejection;
        this.threadLoadJudge = threadLoadJudge;
        this.cpuLoadJudge = cpuLoadJudge;
        this.spinWaitTimeNanos = TimeUnit.MILLISECONDS.toNanos(spinWaitTimeMillis);
        this.blockTimeoutMillis = blockTimeoutMillis;
        this.maxRetryAttempts = maxRetryAttempts;
        this.cpuLoadSampler = new CpuLoadSampler();
        this.cpuLoadSampler.start();
    }

    /**
     * 简化构造器——不启用防拒绝，仅缓冲度控制。
     */
    public AdaptiveBufferedThreadPoolExecutor(int corePoolSize,
                                              int maximumPoolSize,
                                              long keepAliveTime,
                                              TimeUnit unit,
                                              BlockingQueue<Runnable> workQueue,
                                              ThreadFactory threadFactory,
                                              AdaptiveRejectedHandler handler,
                                              double bufferDegree) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler,
                bufferDegree, false, 8, 0.7, 10, 200, 5);
    }

    // ==================== execute ====================

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        // TTL 上下文透传由调用方负责（ThreadPoolExecutorConfig 中通过 TtlExecutors 包装）
        int c = ctl.get();

        // Step 1: 小于 core，新建线程
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true)) {
                return;
            }
            c = ctl.get();
        }

        // Step 2: 队列未达缓冲阈值则入队
        int queueCapacity = workQueue.remainingCapacity() + workQueue.size();
        if (queueCapacity > 0 && workQueue.size() <= (int) (queueCapacity * bufferDegree)) {
            if (isRunning(c) && workQueue.offer(command)) {
                int recheck = ctl.get();
                if (!isRunning(recheck) && remove(command)) {
                    reject(command);
                } else if (workerCountOf(recheck) == 0) {
                    addWorker(null, false);
                }
                return;
            }
        }

        // Step 3: 小于 max，新建线程
        if (workerCountOf(c) <= maximumPoolSize) {
            if (addWorker(command, false)) {
                return;
            }
        }

        // Step 4: 尝试入队，失败则走强制入队或拒绝
        if (!workQueue.offer(command)) {
            if (!forceEnqueue(command, ctl.get())) {
                reject(command);
            }
        }
    }

    // ==================== 强制入队 ====================

    private boolean forceEnqueue(Runnable command, int c) {
        if (!preventRejection) {
            return false;
        }

        double cpuLoad = cpuLoadSampler.getSmoothedLoad();
        int active = activeCount.get();

        if (active > threadLoadJudge && cpuLoad > cpuLoadJudge) {
            // 都高 —— 尝试入队一次，失败就拒绝
            return workQueue.offer(command);
        }

        if (active <= threadLoadJudge && cpuLoad <= cpuLoadJudge) {
            // 线程池负载较低，CPU负载较低 → 阻塞等待
            return blockAndRetry(command);
        }
        if (active <= threadLoadJudge /* && cpuLoad > cpuLoadJudge */) {
            // 线程池负载较低，CPU负载较高 → 阻塞等待
            return blockAndRetry(command);
        }
        // active > threadLoadJudge && cpuLoad <= cpuLoadJudge
        // 线程池负载较高，CPU负载较低 → 空转等待
        return spinAndRetry(command);
    }

    private boolean blockAndRetry(Runnable command) {
        try {
            return workQueue.offer(command, blockTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean spinAndRetry(Runnable command) {
        long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(1000);
        long currentWait = spinWaitTimeNanos;
        for (int retry = 0; retry < maxRetryAttempts; retry++) {
            long start = System.nanoTime();
            long elapsed = 0;
            while (elapsed < currentWait) {
                Thread.onSpinWait();
                elapsed = System.nanoTime() - start;
            }
            if (workQueue.offer(command)) {
                return true;
            }
            currentWait = Math.min(currentWait * 2, maxWaitNanos);
        }
        return false;
    }

    private void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    // ==================== Worker ====================

    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
        private static final long serialVersionUID = 6138294804551838833L;
        final Thread thread;
        Runnable firstTask;
        volatile long completedTasks;

        Worker(Runnable firstTask) {
            setState(-1);
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        @Override
        public void run() {
            runWorker(this);
        }

        @Override
        protected boolean isHeldExclusively() { return getState() != 0; }

        @Override
        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock() { acquire(1); }
        public boolean tryLock() { return tryAcquire(1); }
        public void unlock() { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    // ==================== Worker 管理 ====================

    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (; ; ) {
            int c = ctl.get();
            int rs = runStateOf(c);
            if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                return false;
            }
            for (; ; ) {
                int wc = workerCountOf(c);
                if (wc >= CAPACITY || wc >= (core ? corePoolSize : maximumPoolSize)) {
                    return false;
                }
                if (compareAndIncrementWorkerCount(c)) {
                    break retry;
                }
                c = ctl.get();
                if (runStateOf(c) != rs) {
                    continue retry;
                }
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    int rs = runStateOf(ctl.get());
                    if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) {
                            throw new IllegalThreadStateException();
                        }
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize) {
                            largestPoolSize = s;
                        }
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (!workerStarted) {
                addWorkerFailed(w);
            }
        }
        return workerStarted;
    }

    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null) {
                workers.remove(w);
            }
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        if (completedAbruptly) {
            decrementWorkerCount();
        }
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && !workQueue.isEmpty()) {
                    min = 1;
                }
                if (workerCountOf(c) >= min) {
                    return;
                }
            }
            addWorker(null, false);
        }
    }

    private Runnable getTask() {
        boolean timedOut = false;
        for (; ; ) {
            int c = ctl.get();
            int rs = runStateOf(c);
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }
            int wc = workerCountOf(c);
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;
            if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c)) {
                    return null;
                }
                continue;
            }
            try {
                Runnable r = timed
                        ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS)
                        : workQueue.take();
                if (r != null) {
                    return r;
                }
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock();
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                w.lock();
                if ((runStateAtLeast(ctl.get(), STOP)
                        || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)))
                        && !wt.isInterrupted()) {
                    wt.interrupt();
                }
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x;
                        throw x;
                    } catch (Error x) {
                        thrown = x;
                        throw x;
                    } catch (Throwable x) {
                        thrown = x;
                        throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    // ==================== 活跃线程计数 ====================

    protected void beforeExecute(Thread t, Runnable r) {
        activeCount.incrementAndGet();
    }

    protected void afterExecute(Runnable r, Throwable t) {
        activeCount.decrementAndGet();
    }

    /**
     * 当前活跃（正在执行任务的）线程数。
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    // ==================== 生命周期 ====================

    private void advanceRunState(int targetState) {
        for (; ; ) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState)
                    || ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c)))) {
                break;
            }
        }
    }

    final void tryTerminate() {
        for (; ; ) {
            int c = ctl.get();
            if (isRunning(c)
                    || runStateAtLeast(c, TIDYING)
                    || (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty())) {
                return;
            }
            if (workerCountOf(c) != 0) {
                interruptIdleWorkers(ONLY_ONE);
                return;
            }
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        onTerminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
        }
    }

    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                w.interruptIfStarted();
            }
        } finally {
            mainLock.unlock();
        }
    }

    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne) {
                    break;
                }
            }
        } finally {
            mainLock.unlock();
        }
    }

    private static final boolean ONLY_ONE = true;

    protected void onTerminated() {
    }

    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        List<Runnable> taskList = new java.util.ArrayList<>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r)) {
                    taskList.add(r);
                }
            }
        }
        return taskList;
    }

    @Override
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            cpuLoadSampler.stop();
            advanceRunState(SHUTDOWN);
            interruptIdleWorkers(false);
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            cpuLoadSampler.stop();
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    @Override
    public boolean isShutdown() { return !isRunning(ctl.get()); }

    /**
     * 当前线程池是否正在关闭（已开始关闭但尚未完全终止）。
     *
     * <p>注意：{@link ExecutorService} 在 JDK 17 中未声明此方法（JDK 19 才作为默认方法加入），
     * 故不能使用 {@code @Override}。
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return !isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    @Override
    public boolean isTerminated() { return runStateAtLeast(ctl.get(), TERMINATED); }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (; ; ) {
                if (runStateAtLeast(ctl.get(), TERMINATED)) {
                    return true;
                }
                if (nanos <= 0) {
                    return false;
                }
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    // ==================== ctl CAS 辅助 ====================

    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    private void decrementWorkerCount() {
        do {
        } while (!compareAndDecrementWorkerCount(ctl.get()));
    }

    // ==================== getter/setter ====================

    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0) throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize) {
            interruptIdleWorkers(false);
        } else if (delta > 0) {
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) break;
            }
        }
    }

    public int getCorePoolSize() { return corePoolSize; }

    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize) interruptIdleWorkers(false);
    }

    public int getMaximumPoolSize() { return maximumPoolSize; }

    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0) throw new IllegalArgumentException();
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0) interruptIdleWorkers(false);
    }

    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value) interruptIdleWorkers(false);
        }
    }

    public boolean allowsCoreThreadTimeOut() { return allowCoreThreadTimeOut; }

    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null) throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    public ThreadFactory getThreadFactory() { return threadFactory; }

    public void setRejectedExecutionHandler(AdaptiveRejectedHandler handler) {
        if (handler == null) throw new NullPointerException();
        this.handler = handler;
    }

    public AdaptiveRejectedHandler getRejectedExecutionHandler() { return handler; }

    public BlockingQueue<Runnable> getQueue() { return workQueue; }

    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate();
        return removed;
    }

    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return runStateAtLeast(ctl.get(), TIDYING) ? 0 : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
            }
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked()) nactive++;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = runStateLessThan(c, SHUTDOWN) ? "Running"
                : (runStateAtLeast(c, TERMINATED) ? "Terminated" : "Shutting down");
        return super.toString() + "[" + rs
                + ", pool size = " + nworkers
                + ", active threads = " + nactive
                + ", queued tasks = " + workQueue.size()
                + ", completed tasks = " + ncompleted + "]";
    }

    // ==================== CPU 负载采样 ====================

    /**
     * 基于 JDK 内置 {@code com.sun.management.OperatingSystemMXBean} 的 CPU 负载采样器。
     * 每秒采样一次，使用指数滑动平均 (α=0.3) 平滑毛刺，零外部依赖。
     */
    static final class CpuLoadSampler {
        private static final double ALPHA = 0.3;
        private volatile double smoothedLoad = 0.0;
        private volatile boolean running;
        private Thread samplerThread;

        void start() {
            running = true;
            samplerThread = new Thread(this::sampleLoop, "cpu-load-sampler");
            samplerThread.setDaemon(true);
            samplerThread.start();
        }

        void stop() {
            running = false;
            if (samplerThread != null) {
                samplerThread.interrupt();
            }
        }

        private void sampleLoop() {
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                double instant = getSystemCpuLoad();
                if (instant < 0) {
                    instant = 0;
                }
                // EMA 平滑
                double prev = smoothedLoad;
                smoothedLoad = prev == 0.0 ? instant : prev + ALPHA * (instant - prev);
            }
        }

        /**
         * 通过 JMX 获取 JVM 可见的最近系统 CPU 负载（0.0 ~ 1.0）。
         */
        private static double getSystemCpuLoad() {
            try {
                java.lang.management.OperatingSystemMXBean osBean =
                        ManagementFactory.getOperatingSystemMXBean();
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    double load = sunOsBean.getCpuLoad();
                    return load < 0 ? 0 : load;
                }
            } catch (Exception ignored) {
            }
            // 回退：取系统平均负载 / 核心数
            double avg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            if (avg < 0) return 0;
            int cpus = Runtime.getRuntime().availableProcessors();
            return Math.min(1.0, avg / cpus);
        }

        double getSmoothedLoad() {
            return smoothedLoad;
        }
    }

    // ==================== 内置拒绝策略 ====================

    /**
     * 丢弃策略：静默丢弃。
     */
    public static class DiscardPolicy implements AdaptiveRejectedHandler {
        @Override
        public void rejectedExecution(Runnable r, AdaptiveBufferedThreadPoolExecutor executor) {
            // discard silently
        }
    }

    /**
     * 计数策略：仅计数拒绝次数，便于监控。
     */
    public static class CountPolicy implements AdaptiveRejectedHandler {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void rejectedExecution(Runnable r, AdaptiveBufferedThreadPoolExecutor executor) {
            count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }
    }
}
