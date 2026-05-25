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

package com.nageoffer.ai.ragent.career.media;

import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 面试实时转写会话上下文，用管道流解耦 WebSocket 音频接收和 ASR 下游消费。
 */
public class TranscriptionSessionContext implements Closeable {

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    private static final int AUDIO_QUEUE_CAPACITY = 32;

    private static final long AUDIO_QUEUE_OFFER_TIMEOUT_MS = 100L;

    private static final byte[] STOP_SIGNAL = new byte[0];

    @Getter
    private final String websocketSessionId;

    @Getter
    private final String interviewSessionId;

    @Getter
    private final String userId;

    @Getter
    private final PipedInputStream audioInputStream;

    private final PipedOutputStream audioOutputStream;

    private final BlockingQueue<byte[]> audioQueue;

    private final Thread audioWriterThread;

    private final CareerAstTranscriptionAssembler assembler = new CareerAstTranscriptionAssembler();

    private final AtomicBoolean active = new AtomicBoolean(true);

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private final AtomicReference<IOException> writerError = new AtomicReference<>();

    private final AtomicReference<CareerTranscriptionUpdate> latestUpdate = new AtomicReference<>();

    /**
     * 创建并打开一个实时转写会话上下文。
     */
    public static TranscriptionSessionContext open(String websocketSessionId, String interviewSessionId, String userId) throws IOException {
        return open(websocketSessionId, interviewSessionId, userId, AUDIO_QUEUE_CAPACITY);
    }

    /**
     * 创建指定音频队列容量的上下文，供单元测试覆盖背压边界。
     */
    static TranscriptionSessionContext open(String websocketSessionId,
                                            String interviewSessionId,
                                            String userId,
                                            int audioQueueCapacity) throws IOException {
        PipedInputStream inputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        TranscriptionSessionContext context = new TranscriptionSessionContext(
                websocketSessionId,
                interviewSessionId,
                userId,
                inputStream,
                outputStream,
                audioQueueCapacity
        );
        context.startAudioWriter();
        return context;
    }

    /**
     * 初始化上下文内部管道。
     */
    private TranscriptionSessionContext(String websocketSessionId,
                                        String interviewSessionId,
                                        String userId,
                                        PipedInputStream audioInputStream,
                                        PipedOutputStream audioOutputStream,
                                        int audioQueueCapacity) {
        this.websocketSessionId = websocketSessionId;
        this.interviewSessionId = interviewSessionId;
        this.userId = userId;
        this.audioInputStream = audioInputStream;
        this.audioOutputStream = audioOutputStream;
        this.audioQueue = new ArrayBlockingQueue<>(Math.max(1, audioQueueCapacity));
        this.audioWriterThread = new Thread(this::drainAudioQueue, "career-asr-audio-writer-" + websocketSessionId);
        this.audioWriterThread.setDaemon(true);
    }

    /**
     * 写入一帧音频数据。
     */
    public void writeAudio(ByteBuffer byteBuffer) throws IOException {
        if (byteBuffer == null || !active.get()) {
            return;
        }
        IOException error = writerError.get();
        if (error != null) {
            throw new IOException("音频写入线程已异常", error);
        }
        if (!byteBuffer.hasRemaining()) {
            return;
        }
        byte[] audioData = new byte[byteBuffer.remaining()];
        byteBuffer.get(audioData);
        try {
            boolean offered = audioQueue.offer(audioData, AUDIO_QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!offered) {
                throw new IOException("音频缓冲区已满，实时转写消费过慢");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("音频写入等待被中断", ex);
        }
    }

    /**
     * 应用一个转写分段并保存最新快照。
     */
    public CareerTranscriptionUpdate applySegment(AstTranscriptionSegment segment) {
        CareerTranscriptionUpdate update = assembler.apply(segment);
        latestUpdate.set(update);
        return update;
    }

    /**
     * 获取最新转写快照。
     */
    public CareerTranscriptionUpdate getLatestUpdate() {
        return latestUpdate.get();
    }

    /**
     * 提交最终转写文本，不再把完整文本作为普通分段重复写入组装器。
     */
    public CareerTranscriptionUpdate completeFinalText(String finalText) {
        CareerTranscriptionUpdate previous = latestUpdate.get();
        CareerTranscriptionUpdate update = new CareerTranscriptionUpdate(
                finalText == null ? "" : finalText,
                finalText == null ? "" : finalText,
                "",
                finalText == null ? "" : finalText,
                previous == null || previous.revision() == null ? 1 : previous.revision() + 1,
                "final",
                previous == null ? null : previous.segmentId(),
                finalText,
                previous == null ? "final" : previous.pgs(),
                previous == null ? null : previous.rg(),
                previous == null ? null : previous.bg(),
                previous == null ? null : previous.ed(),
                true
        );
        latestUpdate.set(update);
        return update;
    }

    /**
     * 判断转写会话是否仍在接收音频。
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * 判断是否已请求主动停止。
     */
    public boolean isStopRequested() {
        return stopRequested.get();
    }

    /**
     * 请求停止转写并关闭写入端。
     */
    public void requestStop() {
        active.set(false);
        stopRequested.set(true);
        audioQueue.clear();
        audioQueue.offer(STOP_SIGNAL);
        closeQuietly(audioOutputStream);
    }

    /**
     * 关闭上下文持有的管道资源。
     */
    @Override
    public void close() {
        active.set(false);
        audioQueue.clear();
        audioQueue.offer(STOP_SIGNAL);
        audioWriterThread.interrupt();
        closeQuietly(audioOutputStream);
        closeQuietly(audioInputStream);
    }

    /**
     * 启动音频写入线程。
     */
    private void startAudioWriter() {
        audioWriterThread.start();
    }

    /**
     * 后台消费音频队列并写入 ASR 管道，避免 WebSocket 线程被慢消费阻塞。
     */
    private void drainAudioQueue() {
        try {
            while (active.get() || !audioQueue.isEmpty()) {
                byte[] audioData = audioQueue.take();
                if (audioData == STOP_SIGNAL) {
                    break;
                }
                audioOutputStream.write(audioData);
                audioOutputStream.flush();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            writerError.compareAndSet(null, ex);
            active.set(false);
        } finally {
            closeQuietly(audioOutputStream);
        }
    }

    /**
     * 安静关闭资源，避免清理过程影响主链路。
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 清理阶段忽略关闭异常。
        }
    }
}
