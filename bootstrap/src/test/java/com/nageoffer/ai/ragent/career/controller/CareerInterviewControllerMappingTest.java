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

import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewAnswerRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerInterviewCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CareerInterviewControllerMappingTest {

    @Test
    void interviewEndpointsMatchApiContract() throws Exception {
        assertArrayEquals(new String[]{"/career/interviews"},
                CareerInterviewController.class
                        .getMethod("createSession", CareerInterviewCreateRequest.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}"},
                CareerInterviewController.class
                        .getMethod("querySession", String.class)
                        .getAnnotation(GetMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/next-question"},
                CareerInterviewController.class
                        .getMethod("nextQuestion", String.class)
                        .getAnnotation(GetMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/answers"},
                CareerInterviewController.class
                        .getMethod("submitAnswer", String.class, CareerInterviewAnswerRequest.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/pause"},
                CareerInterviewController.class
                        .getMethod("pause", String.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/finish"},
                CareerInterviewController.class
                        .getMethod("finish", String.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/recover"},
                CareerInterviewController.class
                        .getMethod("recover", String.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/report"},
                CareerInterviewController.class
                        .getMethod("generateReport", String.class)
                        .getAnnotation(PostMapping.class)
                        .value());
        assertArrayEquals(new String[]{"/career/interviews/{sessionId}/report"},
                CareerInterviewController.class
                        .getMethod("queryReport", String.class)
                        .getAnnotation(GetMapping.class)
                        .value());
    }
}
