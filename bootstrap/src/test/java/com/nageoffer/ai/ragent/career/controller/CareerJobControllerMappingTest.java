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

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CareerJobControllerMappingTest {

    @Test
    void alignmentEndpointsMatchApiContract() throws Exception {
        assertArrayEquals(
                new String[]{"/career/alignments"},
                CareerJobController.class
                        .getMethod("align", com.nageoffer.ai.ragent.career.controller.request.CareerAlignmentCreateRequest.class)
                        .getAnnotation(PostMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/career/alignments/{reportId}"},
                CareerJobController.class
                        .getMethod("queryAlignment", String.class)
                        .getAnnotation(GetMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/career/jobs/import-url"},
                CareerJobController.class
                        .getMethod("importJobFromUrl", com.nageoffer.ai.ragent.career.controller.request.CareerJobUrlImportRequest.class)
                        .getAnnotation(PostMapping.class)
                        .value()
        );
    }
}
