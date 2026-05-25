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

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerAstTranscriptionAssemblerTest {

    @Test
    void noPgsPartialReplacesCurrentLiveSnapshot() {
        CareerAstTranscriptionAssembler assembler = new CareerAstTranscriptionAssembler();

        CareerTranscriptionUpdate first = assembler.apply(new AstTranscriptionSegment(1, null, null, 0, 1000, "A", false));
        CareerTranscriptionUpdate second = assembler.apply(new AstTranscriptionSegment(2, null, null, 1001, 2000, "AB", false));
        CareerTranscriptionUpdate third = assembler.apply(new AstTranscriptionSegment(3, null, null, 2001, 3000, "ABC", false));

        assertEquals("A", first.fullText());
        assertEquals("AB", second.fullText());
        assertEquals("", second.committedText());
        assertEquals("AB", second.liveText());
        assertEquals("ABC", third.fullText());
        assertEquals("ABC", third.liveText());
    }

    @Test
    void rplPacketRemovesReplacedSentenceRange() {
        CareerAstTranscriptionAssembler assembler = new CareerAstTranscriptionAssembler();

        assembler.apply(new AstTranscriptionSegment(1, "apd", null, null, null, "first ", false));
        assembler.apply(new AstTranscriptionSegment(2, "apd", null, null, null, "wrong ", false));
        assembler.apply(new AstTranscriptionSegment(3, "apd", null, null, null, "tail", false));
        CareerTranscriptionUpdate update = assembler.apply(new AstTranscriptionSegment(2, "rpl", new int[]{2, 2}, null, null, "correct ", false));

        assertEquals("first correct tail", update.fullText());
        assertEquals("rpl", update.pgs());
        assertEquals(2, update.segmentId());
    }

    @Test
    void finalPacketCommitsSnapshotAndClearsLiveText() {
        CareerAstTranscriptionAssembler assembler = new CareerAstTranscriptionAssembler();

        CareerTranscriptionUpdate update = assembler.apply(new AstTranscriptionSegment(1, null, null, 0, 1000, "final answer", true));

        assertEquals("final answer", update.fullText());
        assertEquals("final answer", update.committedText());
        assertEquals("", update.liveText());
        assertEquals("final", update.resultStatus());
    }

    @Test
    void punctuationOnlyPacketDoesNotDeleteConfirmedText() {
        CareerAstTranscriptionAssembler assembler = new CareerAstTranscriptionAssembler();

        assembler.apply(new AstTranscriptionSegment(1, null, null, 0, 500, "你好", true));
        CareerTranscriptionUpdate update = assembler.apply(new AstTranscriptionSegment(2, null, null, 0, 500, "。", true));

        assertEquals("你好。", update.fullText());
        assertEquals("你好。", update.committedText());
    }
}
