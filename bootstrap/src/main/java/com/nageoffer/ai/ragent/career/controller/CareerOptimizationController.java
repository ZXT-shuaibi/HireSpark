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
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerOptimizationController {

    private final ResumeOptimizationService resumeOptimizationService;

    @PostMapping("/career/optimizations")
    public Result<CareerOptimizationTaskVO> createTask(@RequestBody CareerOptimizationCreateRequest request) {
        return Results.success(resumeOptimizationService.createTask(request));
    }

    @GetMapping("/career/optimizations/{taskId}")
    public Result<CareerOptimizationTaskVO> queryTask(@PathVariable String taskId) {
        return Results.success(resumeOptimizationService.queryTask(taskId));
    }

    @PutMapping("/career/optimizations/suggestions/{suggestionId}")
    public Result<CareerOptimizationSuggestionVO> decideSuggestion(
            @PathVariable String suggestionId,
            @RequestBody CareerSuggestionDecisionRequest request) {
        return Results.success(resumeOptimizationService.decideSuggestion(suggestionId, request));
    }

    @PostMapping("/career/optimizations/{taskId}/versions")
    public Result<CareerResumeVersionVO> generateVersion(@PathVariable String taskId) {
        return Results.success(resumeOptimizationService.generateVersionFromAcceptedSuggestions(taskId));
    }
}
