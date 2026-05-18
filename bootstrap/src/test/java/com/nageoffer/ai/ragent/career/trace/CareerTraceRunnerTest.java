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

package com.nageoffer.ai.ragent.career.trace;

import com.nageoffer.ai.ragent.career.service.trace.CareerTraceRunner;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;

class CareerTraceRunnerTest {

    @Test
    void returnsSupplierResultWithoutTraceWhenDisabled() {
        RagTraceProperties properties = new RagTraceProperties();
        properties.setEnabled(false);
        RecordingTraceRecordService records = new RecordingTraceRecordService();
        CareerTraceRunner runner = new CareerTraceRunner(properties, records);

        String result = runner.run("career-test", "task-1", () -> "ok");

        Assertions.assertEquals("ok", result);
        Assertions.assertNull(records.startedRun);
    }

    @Test
    void recordsSuccessfulRunAndClearsContext() {
        RagTraceProperties properties = new RagTraceProperties();
        properties.setEnabled(true);
        RecordingTraceRecordService records = new RecordingTraceRecordService();
        CareerTraceRunner runner = new CareerTraceRunner(properties, records);

        String result = runner.run("career-test", "task-success", () -> {
            Assertions.assertNotNull(RagTraceContext.getTraceId());
            Assertions.assertEquals("task-success", RagTraceContext.getTaskId());
            return "ok";
        });

        Assertions.assertEquals("ok", result);
        Assertions.assertNotNull(records.startedRun);
        Assertions.assertEquals("SUCCESS", records.finishedStatus);
        Assertions.assertNull(RagTraceContext.getTraceId());
        Assertions.assertNull(RagTraceContext.getTaskId());
    }

    @Test
    void recordsFailedRunAndRethrowsRuntimeException() {
        RagTraceProperties properties = new RagTraceProperties();
        properties.setEnabled(true);
        RecordingTraceRecordService records = new RecordingTraceRecordService();
        CareerTraceRunner runner = new CareerTraceRunner(properties, records);

        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                () -> runner.run("career-test", "task-2", () -> {
                    throw new IllegalStateException("boom");
                })
        );

        Assertions.assertEquals("boom", ex.getMessage());
        Assertions.assertNotNull(records.startedRun);
        Assertions.assertEquals("career-test", records.startedRun.getTraceName());
        Assertions.assertEquals("task-2", records.startedRun.getTaskId());
        Assertions.assertEquals("ERROR", records.finishedStatus);
        Assertions.assertTrue(records.finishedErrorMessage.contains("IllegalStateException"));
        Assertions.assertNull(RagTraceContext.getTraceId());
        Assertions.assertNull(RagTraceContext.getTaskId());
    }

    @Test
    void recordsFailedRunAndRethrowsError() {
        RagTraceProperties properties = new RagTraceProperties();
        properties.setEnabled(true);
        RecordingTraceRecordService records = new RecordingTraceRecordService();
        CareerTraceRunner runner = new CareerTraceRunner(properties, records);

        AssertionError ex = Assertions.assertThrows(
                AssertionError.class,
                () -> runner.run("career-test", "task-error", () -> {
                    throw new AssertionError("fatal");
                })
        );

        Assertions.assertEquals("fatal", ex.getMessage());
        Assertions.assertEquals("ERROR", records.finishedStatus);
        Assertions.assertTrue(records.finishedErrorMessage.contains("AssertionError"));
        Assertions.assertNull(RagTraceContext.getTraceId());
        Assertions.assertNull(RagTraceContext.getTaskId());
    }

    private static class RecordingTraceRecordService implements RagTraceRecordService {

        private RagTraceRunDO startedRun;

        private String finishedStatus;

        private String finishedErrorMessage;

        @Override
        public void startRun(RagTraceRunDO run) {
            this.startedRun = run;
        }

        @Override
        public void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs) {
            this.finishedStatus = status;
            this.finishedErrorMessage = errorMessage;
        }

        @Override
        public void startNode(RagTraceNodeDO node) {
        }

        @Override
        public void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs) {
        }
    }
}
