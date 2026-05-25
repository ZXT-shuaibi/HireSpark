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
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationSuggestionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerOptimizationTaskVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerResumeVersionVO;
import com.nageoffer.ai.ragent.career.service.ResumeOptimizationService;
import com.nageoffer.ai.ragent.career.service.progress.CareerProgressStreamService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@Tag(name = "Career Optimization", description = "简历优化任务、建议决策、版本生成和实时进度接口")
public class CareerOptimizationController {

    private final ResumeOptimizationService resumeOptimizationService;

    private final CareerProgressStreamService careerProgressStreamService;

    @PostMapping("/career/optimizations")
    @Operation(summary = "创建简历优化任务", description = "基于简历版本、JD 或匹配报告异步生成优化建议")
    public Result<CareerOptimizationTaskVO> createTask(@RequestBody CareerOptimizationCreateRequest request) {
        return Results.success(resumeOptimizationService.createTaskAsync(request));
    }

    @GetMapping("/career/optimizations/{taskId}")
    @Operation(summary = "查询优化任务", description = "查询优化任务状态、质量门禁、建议列表和进度事件")
    public Result<CareerOptimizationTaskVO> queryTask(@PathVariable String taskId) {
        return Results.success(resumeOptimizationService.queryTask(taskId));
    }

    /**
     * 订阅简历优化任务的实时进度。
     */
    @GetMapping(value = "/career/optimizations/{taskId}/progress/stream", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "订阅优化实时进度", description = "通过 SSE 接收优化任务进度，断开后可回退到任务详情中的 DB 事件")
    public SseEmitter streamProgress(@PathVariable String taskId) {
        String userId = UserContext.requireUser().getUserId();
        resumeOptimizationService.queryTask(taskId);
        return careerProgressStreamService.subscribeOptimization(taskId, userId);
    }

    @PutMapping("/career/optimizations/suggestions/{suggestionId}")
    @Operation(summary = "处理优化建议", description = "接受、拒绝或调整单条优化建议")
    public Result<CareerOptimizationSuggestionVO> decideSuggestion(
            @PathVariable String suggestionId,
            @RequestBody CareerSuggestionDecisionRequest request) {
        return Results.success(resumeOptimizationService.decideSuggestion(suggestionId, request));
    }

    @PostMapping("/career/optimizations/{taskId}/versions")
    @Operation(summary = "生成优化后简历版本", description = "把已接受建议应用到原简历，生成新的简历版本")
    public Result<CareerResumeVersionVO> generateVersion(@PathVariable String taskId) {
        return Results.success(resumeOptimizationService.generateVersionFromAcceptedSuggestions(taskId));
    }
}
