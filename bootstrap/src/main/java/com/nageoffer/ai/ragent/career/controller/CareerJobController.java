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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Career Job Alignment", description = "岗位 JD 管理和简历匹配分析接口")
public class CareerJobController {

    private final JobAlignmentService jobAlignmentService;

    @PostMapping("/career/jobs")
    @Operation(summary = "创建岗位 JD", description = "保存岗位描述文本，供简历匹配、优化和面试出题复用")
    public Result<CareerJobVO> createJob(@RequestBody CareerJobCreateRequest request) {
        return Results.success(jobAlignmentService.createJob(request));
    }

    @GetMapping("/career/jobs/{jdId}")
    @Operation(summary = "查询岗位 JD", description = "按 JD ID 查询岗位描述和解析结果")
    public Result<CareerJobVO> queryJob(@PathVariable String jdId) {
        return Results.success(jobAlignmentService.queryJob(jdId));
    }

    @PostMapping("/career/alignments")
    @Operation(summary = "创建简历与 JD 匹配分析", description = "基于简历版本和岗位 JD 生成匹配报告")
    public Result<CareerAlignmentReportVO> align(@RequestBody CareerAlignmentCreateRequest request) {
        return Results.success(jobAlignmentService.align(request));
    }

    @GetMapping("/career/alignments/{reportId}")
    @Operation(summary = "查询匹配报告", description = "按报告 ID 查询匹配分、差距、证据和建议")
    public Result<CareerAlignmentReportVO> queryAlignment(@PathVariable String reportId) {
        return Results.success(jobAlignmentService.queryAlignment(reportId));
    }
}
