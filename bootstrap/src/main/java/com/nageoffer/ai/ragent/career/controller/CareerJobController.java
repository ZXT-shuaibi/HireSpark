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

import com.nageoffer.ai.ragent.career.controller.request.CareerAlignmentCreateRequest;
import com.nageoffer.ai.ragent.career.controller.request.CareerJobCreateRequest;
import com.nageoffer.ai.ragent.career.controller.vo.CareerAlignmentReportVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerJobVO;
import com.nageoffer.ai.ragent.career.service.JobAlignmentService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerJobController {

    private final JobAlignmentService jobAlignmentService;

    @PostMapping("/career/jobs")
    public Result<CareerJobVO> createJob(@RequestBody CareerJobCreateRequest request) {
        return Results.success(jobAlignmentService.createJob(request));
    }

    @GetMapping("/career/jobs/{jdId}")
    public Result<CareerJobVO> queryJob(@PathVariable String jdId) {
        return Results.success(jobAlignmentService.queryJob(jdId));
    }

    @PostMapping("/career/alignments")
    public Result<CareerAlignmentReportVO> align(@RequestBody CareerAlignmentCreateRequest request) {
        return Results.success(jobAlignmentService.align(request));
    }

    @GetMapping("/career/alignments/{reportId}")
    public Result<CareerAlignmentReportVO> queryAlignment(@PathVariable String reportId) {
        return Results.success(jobAlignmentService.queryAlignment(reportId));
    }
}
