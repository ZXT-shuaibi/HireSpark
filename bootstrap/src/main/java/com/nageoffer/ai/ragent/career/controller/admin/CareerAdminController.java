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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/career")
public class CareerAdminController {

    private final CareerAdminService careerAdminService;

    @GetMapping("/overview")
    public Result<CareerAdminOverviewVO> overview() {
        return Results.success(careerAdminService.overview());
    }

    @GetMapping("/tasks")
    public Result<List<CareerAdminTaskItemVO>> tasks(@RequestParam(defaultValue = "20") Integer limit,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String status) {
        return Results.success(careerAdminService.tasks(limit, type, status));
    }

    @GetMapping("/rubrics")
    public Result<List<CareerAdminRubricVO>> rubrics() {
        return Results.success(careerAdminService.rubrics());
    }

    /**
     * 查询最近的 Career Agent 调用观测记录。
     */
    @GetMapping("/agent-traces")
    public Result<List<CareerAdminAgentTraceVO>> agentTraces(@RequestParam(defaultValue = "20") Integer limit,
                                                             @RequestParam(required = false) String agentType,
                                                             @RequestParam(required = false) String status) {
        return Results.success(careerAdminService.agentTraces(limit, agentType, status));
    }
}
