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

package com.nageoffer.ai.ragent.career.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerInterviewReportVO;
import com.nageoffer.ai.ragent.career.controller.vo.CareerRadarItemVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewTurnMapper;
import com.nageoffer.ai.ragent.career.service.impl.InterviewReportServiceImpl;
import com.nageoffer.ai.ragent.career.service.impl.InterviewReportSupport;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.report.CareerRadarComputationService;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewReportAggregationTest {

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private InterviewReportMapper reportMapper;

    @Mock
    private CareerJsonParser careerJsonParser;

    @Mock
    private CareerSingleFlightLlmService singleFlightLlmService;

    @Mock
    private CareerRadarComputationService careerRadarComputationService;

    private final List<InterviewReportDO> reports = new ArrayList<>();

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewSessionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewTurnDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewReportDO.class);
    }

    @AfterEach
    void tearDown() {
        reports.clear();
        UserContext.clear();
    }

    @Test
    void generateFallsBackToRoundedAverageWhenOverallScoreMissing() {
        login();
        stubSession();
        stubScoredTurns();
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"summary\":\"ok\"}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(llmReportWithoutScore());
        lenient().when(reportMapper.insert(any(InterviewReportDO.class))).thenAnswer(invocation -> {
            InterviewReportDO report = invocation.getArgument(0);
            report.setId("report-1");
            reports.add(report);
            return 1;
        });
        lenient().when(careerRadarComputationService.compute(anyList(), anyMap()))
                .thenReturn(List.of(CareerRadarItemVO.builder()
                        .dimension("technical")
                        .score(88)
                        .weight(35)
                        .comment("backend radar")
                        .source("TEST")
                        .build()));

        CareerInterviewReportVO result = newService().generate("session-1");

        assertEquals(86, result.getOverallScore());
        assertEquals("session-1", result.getSessionId());
        assertEquals("Solid answers", result.getSummary());
        assertEquals(1, reports.size());
        assertEquals(86, reports.get(0).getOverallScore());
        assertTrue(reports.get(0).getSuggestionsJson().contains("AVERAGE_SCORE_FALLBACK"));
        assertTrue(reports.get(0).getRadarJson().contains("backend radar"));
        assertEquals(2, result.getPlayback().size());
        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(singleFlightLlmService).chat(anyString(), anyString(), anyString(), chatCaptor.capture());
        String prompt = chatCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("\"turnNo\":1"));
        assertTrue(prompt.contains("\"score\":80"));
        assertTrue(prompt.contains("\"feedback\""));
    }

    @Test
    void queryBySessionReturnsLatestReportForCurrentUser() {
        login();
        stubSession();
        lenient().when(reportMapper.selectOne(anyReportWrapper()))
                .thenReturn(report("report-new", "New report", 91, new Date(2000L)));

        CareerInterviewReportVO result = newService().queryBySession("session-1");

        assertEquals("report-new", result.getId());
        assertEquals(91, result.getOverallScore());
        assertEquals("New report", result.getSummary());
        assertEquals(1, result.getRadar().size());
        assertEquals(1, result.getPlayback().size());
        assertEquals(1, result.getSuggestions().size());
        assertNotNull(result.getCreateTime());
    }

    @Test
    void generateWrapsMalformedLlmJsonAsServiceException() {
        login();
        stubSession();
        stubScoredTurns();
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("not json");
        when(careerJsonParser.parseObject(anyString())).thenThrow(new IllegalArgumentException("bad json"));

        assertThrows(ServiceException.class, () -> newService().generate("session-1"));
    }

    @Test
    void generateRejectsSessionsWithoutScoredTurns() {
        login();
        stubSession();
        lenient().when(turnMapper.selectList(anyTurnWrapper())).thenReturn(List.of(
                InterviewTurnDO.builder()
                        .id("turn-1")
                        .sessionId("session-1")
                        .userId("user-1")
                        .turnNo(1)
                        .turnType("TECHNICAL")
                        .question("Q")
                        .answer("A")
                        .feedbackJson("{}")
                        .build()
        ));

        assertThrows(ClientException.class, () -> newService().generate("session-1"));
    }

    @Test
    void queryBySessionRejectsMissingReport() {
        login();
        stubSession();
        lenient().when(reportMapper.selectOne(anyReportWrapper())).thenReturn(null);

        assertThrows(ClientException.class, () -> newService().queryBySession("session-1"));
    }

    private InterviewReportServiceImpl newService() {
        return new InterviewReportServiceImpl(
                sessionMapper,
                turnMapper,
                reportMapper,
                new InterviewReportSupport(careerJsonParser),
                singleFlightLlmService,
                careerRadarComputationService
        );
    }

    private void login() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
    }

    private void stubSession() {
        lenient().when(sessionMapper.selectOne(anySessionWrapper())).thenReturn(InterviewSessionDO.builder()
                .id("session-1")
                .userId("user-1")
                .build());
    }

    private void stubScoredTurns() {
        lenient().when(turnMapper.selectList(anyTurnWrapper())).thenReturn(List.of(
                InterviewTurnDO.builder()
                        .id("turn-1")
                        .sessionId("session-1")
                        .userId("user-1")
                        .turnNo(1)
                        .turnType("TECHNICAL")
                        .question("How do you tune SQL?")
                        .answer("I inspect query plans.")
                        .score(80)
                        .feedbackJson("{\"summary\":\"clear\"}")
                        .build(),
                InterviewTurnDO.builder()
                        .id("turn-2")
                        .sessionId("session-1")
                        .userId("user-1")
                        .turnNo(2)
                        .turnType("PROJECT_DEEP_DIVE")
                        .question("Describe a project.")
                        .answer("I built a platform.")
                        .score(91)
                        .feedbackJson("{\"summary\":\"specific\"}")
                        .build()
        ));
    }

    private Map<String, Object> llmReportWithoutScore() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("radar", List.of(Map.of("dimension", "technical", "score", 86, "comment", "good")));
        report.put("playback", List.of(
                Map.of("question", "How do you tune SQL?", "answer", "I inspect query plans.", "score", 80),
                Map.of("question", "Describe a project.", "answer", "I built a platform.", "score", 91)
        ));
        report.put("suggestions", List.of(Map.of("title", "Practice tradeoffs", "action", "Add constraints", "priority", "HIGH")));
        report.put("summary", "Solid answers");
        return report;
    }

    private InterviewReportDO report(String id, String summary, int score, Date createTime) {
        return InterviewReportDO.builder()
                .id(id)
                .sessionId("session-1")
                .userId("user-1")
                .overallScore(score)
                .radarJson("[{\"dimension\":\"technical\",\"score\":" + score + "}]")
                .playbackJson("[{\"question\":\"Q\",\"answer\":\"A\",\"score\":" + score + "}]")
                .suggestionsJson("[{\"title\":\"Improve\",\"action\":\"Practice\",\"priority\":\"MEDIUM\"}]")
                .summary(summary)
                .traceId("trace-" + id)
                .createTime(createTime)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewSessionDO> anySessionWrapper() {
        return (Wrapper<InterviewSessionDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewTurnDO> anyTurnWrapper() {
        return (Wrapper<InterviewTurnDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewReportDO> anyReportWrapper() {
        return (Wrapper<InterviewReportDO>) any(Wrapper.class);
    }
}
