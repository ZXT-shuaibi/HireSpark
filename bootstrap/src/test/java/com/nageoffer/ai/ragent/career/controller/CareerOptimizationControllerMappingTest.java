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

package com.nageoffer.ai.ragent.career.controller;

import com.nageoffer.ai.ragent.career.controller.request.CareerOptimizationCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerSuggestionDecisionRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationTaskVO;
import com.nageoffer.ai.ragent.career.service.ResumeOptimizationService;
import com.nageoffer.ai.ragent.career.service.progress.CareerProgressStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerOptimizationControllerMappingTest {

    @Test
    void optimizationEndpointsMatchApiContract() throws Exception {
        assertArrayEquals(new String[]{"/career/optimizations"},
                CareerOptimizationController.class
                        .getMethod("createTask", CareerOptimizationCreateRequest.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/optimizations/{taskId}"},
                CareerOptimizationController.class
                        .getMethod("queryTask", String.class)
                        .getAnnotation(GetMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/optimizations/{taskId}/progress/stream"},
                CareerOptimizationController.class
                        .getMethod("streamProgress", String.class)
                        .getAnnotation(GetMapping.class)
                        .value());
        assertEquals("text/event-stream;charset=UTF-8",
                CareerOptimizationController.class
                        .getMethod("streamProgress", String.class)
                        .getAnnotation(GetMapping.class)
                        .produces()[0]);
        assertArrayEquals(new String[]{"/career/optimizations/suggestions/{suggestionId}"},
                CareerOptimizationController.class
                        .getMethod("decideSuggestion", String.class, CareerSuggestionDecisionRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/optimizations/{taskId}/versions"},
                CareerOptimizationController.class
                        .getMethod("generateVersion", String.class)
                        .getAnnotation(PostMapping.class)
                        .value());
    }

    @Test
    void createTaskUsesAsyncServiceForStreamingProgress() {
        ResumeOptimizationService optimizationService = mock(ResumeOptimizationService.class);
        CareerProgressStreamService streamService = mock(CareerProgressStreamService.class);
        CareerOptimizationCreateRequest request = new CareerOptimizationCreateRequest();
        CareerOptimizationTaskVO runningTask = CareerOptimizationTaskVO.builder()
                .id("task-1")
                .status("RUNNING")
                .build();
        when(optimizationService.createTaskAsync(same(request))).thenReturn(runningTask);

        CareerOptimizationController controller = new CareerOptimizationController(optimizationService, streamService);

        assertEquals(runningTask, controller.createTask(request).getData());
        verify(optimizationService).createTaskAsync(same(request));
    }
}
