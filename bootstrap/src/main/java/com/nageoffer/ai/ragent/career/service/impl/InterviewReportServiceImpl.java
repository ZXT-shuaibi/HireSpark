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

package com.nageoffer.ai.ragent.career.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewReportVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.service.InterviewReportService;
import com.nageoffer.ai.ragent.career.service.prompt.CareerPromptTemplates;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewReportServiceImpl implements InterviewReportService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewReportSupport interviewReportSupport;
    private final CareerSingleFlightLlmService singleFlightLlmService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CareerInterviewReportVO generate(String sessionId) {
        String userId = requireUserId();
        InterviewSessionDO session = requireSession(sessionId, userId);
        List<InterviewTurnDO> scoredTurns = scoredTurns(session.getId(), userId);
        if (scoredTurns.isEmpty()) {
            throw new ClientException("Interview report requires at least one scored turn");
        }

        String turnsJson = interviewReportSupport.writeJson(
                interviewReportSupport.toPromptTurns(scoredTurns),
                "Failed to serialize interview turns JSON"
        );
        String prompt = String.format(CareerPromptTemplates.INTERVIEW_REPORT, turnsJson);
        String traceId = StrUtil.blankToDefault(session.getTraceId(), "career-interview-report-" + session.getId());
        Map<String, Object> parsed = interviewReportSupport.parseReport(
                singleFlightLlmService.chat("INTERVIEW_REPORT",
                        buildSingleFlightKey("INTERVIEW_REPORT", userId, session.getId(), turnsJson),
                        traceId,
                        ChatRequest.builder()
                        .messages(List.of(ChatMessage.user(prompt)))
                        .temperature(0.1D)
                        .thinking(false)
                        .build()));

        int averageScore = interviewReportSupport.averageScore(scoredTurns);
        Integer overallScore = interviewReportSupport.readValidScore(parsed.get("overallScore"));
        List<Object> suggestions = interviewReportSupport.toList(parsed.get("suggestions"));
        if (overallScore == null) {
            overallScore = averageScore;
            suggestions = interviewReportSupport.appendFallbackMarker(suggestions, averageScore);
        }

        InterviewReportDO report = InterviewReportDO.builder()
                .sessionId(session.getId())
                .userId(userId)
                .overallScore(overallScore)
                .radarJson(interviewReportSupport.writeJson(
                        interviewReportSupport.toList(parsed.get("radar")),
                        "Failed to serialize interview report radar JSON"))
                .playbackJson(interviewReportSupport.writeJson(
                        interviewReportSupport.toList(parsed.get("playback")),
                        "Failed to serialize interview report playback JSON"))
                .suggestionsJson(interviewReportSupport.writeJson(suggestions,
                        "Failed to serialize interview report suggestions JSON"))
                .summary(interviewReportSupport.extractString(parsed.get("summary")))
                .traceId(traceId)
                .build();
        reportMapper.insert(report);
        return interviewReportSupport.toReportVO(report,
                interviewReportSupport.toList(parsed.get("radar")),
                interviewReportSupport.toList(parsed.get("playback")),
                suggestions);
    }

    @Override
    public CareerInterviewReportVO queryBySession(String sessionId) {
        String userId = requireUserId();
        requireSession(sessionId, userId);
        InterviewReportDO report = reportMapper.selectOne(Wrappers.lambdaQuery(InterviewReportDO.class)
                .eq(InterviewReportDO::getSessionId, sessionId)
                .eq(InterviewReportDO::getUserId, userId)
                .eq(InterviewReportDO::getDeleted, 0)
                .orderByDesc(InterviewReportDO::getCreateTime)
                .last("LIMIT 1"));
        if (report == null) {
            throw new ClientException("Interview report does not exist");
        }
        return interviewReportSupport.toReportVO(report);
    }

    private InterviewSessionDO requireSession(String sessionId, String userId) {
        if (StrUtil.isBlank(sessionId)) {
            throw new ClientException("Interview session id is required");
        }
        InterviewSessionDO session = sessionMapper.selectOne(Wrappers.lambdaQuery(InterviewSessionDO.class)
                .eq(InterviewSessionDO::getId, sessionId)
                .eq(InterviewSessionDO::getUserId, userId)
                .eq(InterviewSessionDO::getDeleted, 0));
        if (session == null) {
            throw new ClientException("Interview session does not exist");
        }
        return session;
    }

    private List<InterviewTurnDO> scoredTurns(String sessionId, String userId) {
        List<InterviewTurnDO> turns = turnMapper.selectList(Wrappers.lambdaQuery(InterviewTurnDO.class)
                .eq(InterviewTurnDO::getSessionId, sessionId)
                .eq(InterviewTurnDO::getUserId, userId)
                .eq(InterviewTurnDO::getDeleted, 0)
                .orderByAsc(InterviewTurnDO::getTurnNo));
        if (turns == null) {
            return List.of();
        }
        return turns.stream()
                .filter(turn -> turn.getScore() != null)
                .toList();
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }

    private String buildSingleFlightKey(String scene, String userId, String artifactId, String prompt) {
        return String.join(":",
                StrUtil.blankToDefault(scene, "CAREER_AI"),
                StrUtil.blankToDefault(userId, "anonymous"),
                StrUtil.blankToDefault(artifactId, "artifact"),
                StrUtil.blankToDefault(prompt, ""));
    }
}
