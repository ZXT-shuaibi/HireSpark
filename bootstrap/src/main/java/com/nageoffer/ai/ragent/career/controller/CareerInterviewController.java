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
import com.nageoffer.ai.ragent.career.service.recovery.InterviewSessionRecoveryService;
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
public class CareerInterviewController {

    private final InterviewSessionService interviewSessionService;

    private final InterviewReportService interviewReportService;

    private final InterviewSessionRecoveryService interviewSessionRecoveryService;

    @PostMapping("/career/interviews")
    public Result<CareerInterviewSessionVO> createSession(@RequestBody CareerInterviewCreateRequest request) {
        return Results.success(interviewSessionService.createSession(request));
    }

    @GetMapping("/career/interviews/{sessionId}")
    public Result<CareerInterviewSessionVO> querySession(@PathVariable String sessionId) {
        return Results.success(interviewSessionService.querySession(sessionId));
    }

    @GetMapping("/career/interviews/{sessionId}/next-question")
    public Result<CareerInterviewTurnVO> nextQuestion(@PathVariable String sessionId) {
        return Results.success(interviewSessionService.nextQuestion(sessionId));
    }

    @PostMapping("/career/interviews/{sessionId}/answers")
    public Result<CareerInterviewTurnVO> submitAnswer(@PathVariable String sessionId,
                                                      @RequestBody CareerInterviewAnswerRequest request) {
        return Results.success(interviewSessionService.submitAnswer(sessionId, request));
    }

    @PostMapping("/career/interviews/{sessionId}/pause")
    public Result<Void> pause(@PathVariable String sessionId) {
        interviewSessionService.pause(sessionId);
        return Results.success();
    }

    @PostMapping("/career/interviews/{sessionId}/finish")
    public Result<Void> finish(@PathVariable String sessionId) {
        interviewSessionService.finish(sessionId);
        return Results.success();
    }

    @PostMapping("/career/interviews/{sessionId}/recover")
    public Result<CareerInterviewSessionVO> recover(@PathVariable String sessionId) {
        return Results.success(interviewSessionRecoveryService.recover(sessionId));
    }

    @PostMapping("/career/interviews/{sessionId}/report")
    public Result<CareerInterviewReportVO> generateReport(@PathVariable String sessionId) {
        return Results.success(interviewReportService.generate(sessionId));
    }

    @GetMapping("/career/interviews/{sessionId}/report")
    public Result<CareerInterviewReportVO> queryReport(@PathVariable String sessionId) {
        return Results.success(interviewReportService.queryBySession(sessionId));
    }
}
