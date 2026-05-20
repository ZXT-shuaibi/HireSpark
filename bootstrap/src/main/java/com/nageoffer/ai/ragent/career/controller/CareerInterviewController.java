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
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewReportVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewSessionVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewTurnVO;
import com.nageoffer.ai.ragent.career.service.InterviewReportService;
import com.nageoffer.ai.ragent.career.service.InterviewSessionService;
import com.nageoffer.ai.ragent.career.service.progress.CareerProgressStreamService;
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionRecoveryService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@Tag(name = "Career Interview", description = "模拟面试会话、答题评分、恢复、报告和实时进度接口")
public class CareerInterviewController {

    private final InterviewSessionService interviewSessionService;

    private final InterviewReportService interviewReportService;

    private final InterviewSessionRecoveryService interviewSessionRecoveryService;

    private final CareerProgressStreamService careerProgressStreamService;

    @PostMapping("/career/interviews")
    @Operation(summary = "创建面试会话", description = "基于简历版本和可选 JD 创建文字模拟面试会话")
    public Result<CareerInterviewSessionVO> createSession(@RequestBody CareerInterviewCreateRequest request) {
        return Results.success(interviewSessionService.createSession(request));
    }

    @GetMapping("/career/interviews/{sessionId}")
    @Operation(summary = "查询面试会话", description = "查询会话状态、阶段、轮次和恢复信息")
    public Result<CareerInterviewSessionVO> querySession(@PathVariable String sessionId) {
        return Results.success(interviewSessionService.querySession(sessionId));
    }

    /**
     * 订阅面试会话的实时进度。
     */
    @GetMapping(value = "/career/interviews/{sessionId}/progress/stream", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "订阅面试实时进度", description = "通过 SSE 接收面试运行时进度，断开后可通过会话详情恢复")
    public SseEmitter streamProgress(@PathVariable String sessionId) {
        String userId = UserContext.requireUser().getUserId();
        interviewSessionService.querySession(sessionId);
        return careerProgressStreamService.subscribeInterview(sessionId, userId);
    }

    @GetMapping("/career/interviews/{sessionId}/next-question")
    @Operation(summary = "获取下一题", description = "按当前会话阶段和计划获取下一道面试题")
    public Result<CareerInterviewTurnVO> nextQuestion(@PathVariable String sessionId) {
        return Results.success(interviewSessionService.nextQuestion(sessionId));
    }

    @PostMapping("/career/interviews/{sessionId}/answers")
    @Operation(summary = "提交面试答案", description = "提交当前轮次答案并触发评分、追问决策和状态推进")
    public Result<CareerInterviewTurnVO> submitAnswer(@PathVariable String sessionId,
                                                      @RequestBody CareerInterviewAnswerRequest request) {
        return Results.success(interviewSessionService.submitAnswer(sessionId, request));
    }

    /**
     * 手动重试指定轮次的面试评分。
     */
    @PostMapping("/career/interviews/{sessionId}/turns/{turnNo}/retry-evaluation")
    @Operation(summary = "重试轮次评分", description = "对指定面试轮次重新执行评分，保留原始答案和审计链路")
    public Result<CareerInterviewTurnVO> retryEvaluation(@PathVariable String sessionId,
                                                         @PathVariable Integer turnNo) {
        return Results.success(interviewSessionService.retryEvaluation(sessionId, turnNo));
    }

    @PostMapping("/career/interviews/{sessionId}/pause")
    @Operation(summary = "暂停面试会话", description = "暂停当前面试会话并保留可恢复快照")
    public Result<Void> pause(@PathVariable String sessionId) {
        interviewSessionService.pause(sessionId);
        return Results.success();
    }

    @PostMapping("/career/interviews/{sessionId}/finish")
    @Operation(summary = "结束面试会话", description = "结束当前面试会话，后续可生成面试报告")
    public Result<Void> finish(@PathVariable String sessionId) {
        interviewSessionService.finish(sessionId);
        return Results.success();
    }

    @PostMapping("/career/interviews/{sessionId}/recover")
    @Operation(summary = "恢复面试会话", description = "从热快照、冷快照或归档轮次恢复面试运行时")
    public Result<CareerInterviewSessionVO> recover(@PathVariable String sessionId) {
        return Results.success(interviewSessionRecoveryService.recover(sessionId));
    }

    @PostMapping("/career/interviews/{sessionId}/report")
    @Operation(summary = "生成面试报告", description = "基于面试轮次、评分和追问结果生成结构化报告")
    public Result<CareerInterviewReportVO> generateReport(@PathVariable String sessionId) {
        return Results.success(interviewReportService.generate(sessionId));
    }

    @GetMapping("/career/interviews/{sessionId}/report")
    @Operation(summary = "查询面试报告", description = "查询指定面试会话的报告内容和雷达评估结果")
    public Result<CareerInterviewReportVO> queryReport(@PathVariable String sessionId) {
        return Results.success(interviewReportService.queryBySession(sessionId));
    }
}
