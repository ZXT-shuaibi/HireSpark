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

package com.nageoffer.ai.ragent.career.controller.admin;

import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminAgentTraceVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminOverviewVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminRubricVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminTaskItemVO;
import com.nageoffer.ai.ragent.career.service.admin.CareerAdminService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/career")
@Tag(name = "Career Admin", description = "Career 管理端概览、任务、Rubric 和 Agent Trace 接口")
public class CareerAdminController {

    private final CareerAdminService careerAdminService;

    @GetMapping("/overview")
    @Operation(summary = "查询 Career 概览", description = "返回简历、优化、面试、报告、导出和失败任务的基础统计")
    public Result<CareerAdminOverviewVO> overview() {
        return Results.success(careerAdminService.overview());
    }

    @GetMapping("/tasks")
    @Operation(summary = "查询 Career 任务列表", description = "按类型和状态筛选最近 Career 任务，辅助定位失败原因和 trace")
    public Result<List<CareerAdminTaskItemVO>> tasks(@RequestParam(defaultValue = "20") Integer limit,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String status) {
        return Results.success(careerAdminService.tasks(limit, type, status));
    }

    @GetMapping("/rubrics")
    @Operation(summary = "查询面试 Rubric", description = "查询当前内置的只读面试评分 Rubric")
    public Result<List<CareerAdminRubricVO>> rubrics() {
        return Results.success(careerAdminService.rubrics());
    }

    /**
     * 查询最近的 Career Agent 调用观测记录。
     */
    @GetMapping("/agent-traces")
    @Operation(summary = "查询 Career Agent Trace", description = "查询最近 Career Agent 或 LLM 调用的脱敏观测记录")
    public Result<List<CareerAdminAgentTraceVO>> agentTraces(@RequestParam(defaultValue = "20") Integer limit,
                                                             @RequestParam(required = false) String agentType,
                                                             @RequestParam(required = false) String status) {
        return Results.success(careerAdminService.agentTraces(limit, agentType, status));
    }
}
