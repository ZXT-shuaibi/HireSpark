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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionSessionContextTest {

    @Test
    void writeAudioKeepsInputStreamDecoupledFromWebSocketThread() throws Exception {
        TranscriptionSessionContext context = TranscriptionSessionContext.open("ws-1", "session-1", "user-1");

        byte[] bytes = "audio".getBytes(StandardCharsets.UTF_8);
        context.writeAudio(ByteBuffer.wrap(bytes));

        byte[] actual = context.getAudioInputStream().readNBytes(bytes.length);
        assertArrayEquals(bytes, actual);

        context.close();
        assertFalse(context.isActive());
    }

    @Test
    void applySegmentStoresLatestSnapshotForAnswerDraft() throws Exception {
        TranscriptionSessionContext context = TranscriptionSessionContext.open("ws-1", "session-1", "user-1");

        CareerTranscriptionUpdate update = context.applySegment(
                new AstTranscriptionSegment(1, null, null, 0, 1000, "answer", true)
        );

        assertTrue(context.isActive());
        assertEquals("answer", update.fullText());
        assertEquals("answer", context.getLatestUpdate().fullText());

        context.requestStop();
        assertFalse(context.isActive());
        assertTrue(context.isStopRequested());
    }

    @Test
    void writeAudioFailsFastWhenDownstreamDoesNotConsume() throws Exception {
        TranscriptionSessionContext context = TranscriptionSessionContext.open("ws-1", "session-1", "user-1", 1);
        byte[] largeChunk = new byte[128 * 1024];

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            boolean rejected = false;
            for (int i = 0; i < 6; i++) {
                try {
                    context.writeAudio(ByteBuffer.wrap(largeChunk));
                } catch (Exception ex) {
                    rejected = true;
                    break;
                }
            }
            assertTrue(rejected);
        });

        context.close();
    }
}
