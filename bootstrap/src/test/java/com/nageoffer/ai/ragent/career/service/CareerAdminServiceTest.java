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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminOverviewVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminRubricVO;
import com.nageoffer.ai.ragent.career.controller.vo.admin.CareerAdminTaskItemVO;
import com.nageoffer.ai.ragent.career.dao.entity.CandidateProfileDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerSingleFlightRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.CareerTaskAttemptDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewSessionDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeDocumentDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeExportRecordDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationReviewDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeOptimizationTaskDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.CandidateProfileMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerSingleFlightRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.CareerTaskAttemptMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewSessionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeDocumentMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeExportRecordMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationReviewMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeOptimizationTaskMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.enums.CareerTaskStatus;
import com.nageoffer.ai.ragent.career.enums.InterviewSessionStatus;
import com.nageoffer.ai.ragent.career.enums.OptimizationReviewStatus;
import com.nageoffer.ai.ragent.career.service.admin.CareerAdminService;
import com.nageoffer.ai.ragent.career.service.admin.impl.CareerAdminServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerAdminServiceTest {

    @Mock
    private ResumeDocumentMapper resumeDocumentMapper;

    @Mock
    private CandidateProfileMapper candidateProfileMapper;

    @Mock
    private ResumeVersionMapper resumeVersionMapper;

    @Mock
    private JobDescriptionMapper jobDescriptionMapper;

    @Mock
    private JobAlignmentReportMapper jobAlignmentReportMapper;

    @Mock
    private ResumeOptimizationTaskMapper resumeOptimizationTaskMapper;

    @Mock
    private ResumeOptimizationReviewMapper resumeOptimizationReviewMapper;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private InterviewReportMapper interviewReportMapper;

    @Mock
    private CareerSingleFlightRecordMapper careerSingleFlightRecordMapper;

    @Mock
    private CareerTaskAttemptMapper careerTaskAttemptMapper;

    @Mock
    private ResumeExportRecordMapper resumeExportRecordMapper;

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeDocumentDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CandidateProfileDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobDescriptionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobAlignmentReportDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeOptimizationTaskDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeOptimizationReviewDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewSessionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), InterviewReportDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerSingleFlightRecordDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CareerTaskAttemptDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeExportRecordDO.class);
    }

    @Test
    void overviewAggregatesCountsAndStatuses() {
        when(resumeDocumentMapper.selectCount(any())).thenReturn(3L);
        when(candidateProfileMapper.selectCount(any())).thenReturn(2L);
        when(resumeVersionMapper.selectCount(any())).thenReturn(5L);
        when(jobDescriptionMapper.selectCount(any())).thenReturn(4L);
        when(jobAlignmentReportMapper.selectCount(any())).thenReturn(6L);
        when(resumeOptimizationTaskMapper.selectCount(any())).thenReturn(7L);
        when(resumeOptimizationReviewMapper.selectCount(any())).thenReturn(1L, 2L, 3L);
        when(interviewSessionMapper.selectCount(any())).thenReturn(8L);
        when(interviewReportMapper.selectCount(any())).thenReturn(9L);
        when(careerSingleFlightRecordMapper.selectCount(any())).thenReturn(10L, 4L, 3L);
        when(careerTaskAttemptMapper.selectCount(any())).thenReturn(12L, 2L, 5L);
        when(resumeExportRecordMapper.selectCount(any())).thenReturn(11L);

        CareerAdminOverviewVO overview = newService().overview();

        assertEquals(3L, overview.getResumeDocuments());
        assertEquals(2L, overview.getCandidateProfiles());
        assertEquals(5L, overview.getResumeVersions());
        assertEquals(4L, overview.getJobDescriptions());
        assertEquals(6L, overview.getAlignmentReports());
        assertEquals(7L, overview.getOptimizationTasks());
        assertEquals(1L, overview.getOptimizationReviewPassed());
        assertEquals(2L, overview.getOptimizationReviewNeedsRevision());
        assertEquals(3L, overview.getOptimizationReviewBlockedByRisk());
        assertEquals(8L, overview.getInterviewSessions());
        assertEquals(9L, overview.getInterviewReports());
        assertEquals(10L, overview.getSingleFlightRecords());
        assertEquals(4L, overview.getSingleFlightRunning());
        assertEquals(3L, overview.getSingleFlightSuccess());
        assertEquals(12L, overview.getTaskAttempts());
        assertEquals(2L, overview.getTaskAttemptFailed());
        assertEquals(5L, overview.getTaskAttemptReplayed());
        assertEquals(11L, overview.getFailedExports());
    }

    @Test
    void tasksNormalizeRecordsAcrossCareerSources() {
        when(resumeDocumentMapper.selectList(any())).thenReturn(List.of(resumeDocument()));
        when(jobAlignmentReportMapper.selectList(any())).thenReturn(List.of(alignmentReport()));
        when(resumeOptimizationTaskMapper.selectList(any())).thenReturn(List.of(optimizationTask()));
        when(resumeOptimizationReviewMapper.selectList(any())).thenReturn(List.of(optimizationReview()));
        when(interviewSessionMapper.selectList(any())).thenReturn(List.of(interviewSession()));
        when(interviewReportMapper.selectList(any())).thenReturn(List.of(interviewReport()));
        when(resumeExportRecordMapper.selectList(any())).thenReturn(List.of(exportRecord()));
        when(careerSingleFlightRecordMapper.selectList(any())).thenReturn(List.of(singleFlightRecord()));
        when(careerTaskAttemptMapper.selectList(any())).thenReturn(List.of(taskAttempt()));

        List<CareerAdminTaskItemVO> tasks = newService().tasks(20, null, null);

        assertEquals(8, tasks.size());
        assertEquals("TASK_ATTEMPT", tasks.get(0).getType());
        assertEquals("attempt-1", tasks.get(0).getId());
        assertEquals("resume-document-1", tasks.get(7).getId());
        assertEquals("SUCCESS", tasks.get(0).getStatus());
        assertEquals("OPTIMIZATION_EXECUTOR", tasks.get(0).getScene());
        assertEquals(128L, tasks.get(0).getLatencyMs());
        assertEquals(new BigDecimal("0.87"), tasks.stream()
                .filter(task -> "OPTIMIZATION_TASK".equals(task.getType()))
                .findFirst().orElseThrow().getQualityScore());
        assertEquals(3, tasks.stream()
                .filter(task -> "INTERVIEW_SESSION".equals(task.getType()))
                .findFirst().orElseThrow().getCurrentTurnNo());
        assertEquals("failed because of export error", tasks.stream()
                .filter(task -> "EXPORT_RECORD".equals(task.getType()))
                .findFirst().orElseThrow().getFailureReason());
        assertEquals("PASSED", tasks.stream()
                .filter(task -> "OPTIMIZATION_TASK".equals(task.getType()))
                .findFirst().orElseThrow().getReviewStatus());
        assertEquals("RUNNING", tasks.stream()
                .filter(task -> "SINGLE_FLIGHT".equals(task.getType()))
                .findFirst().orElseThrow().getRuntimeStatus());
    }

    @Test
    void rubricsAreReadOnlyAndVersioned() {
        List<CareerAdminRubricVO> rubrics = newService().rubrics();

        assertEquals(2, rubrics.size());
        assertEquals("career-java-backend-v1", rubrics.get(0).getId());
        assertFalse(rubrics.get(0).getEditable());
        assertTrue(rubrics.get(0).getDimensions().stream().anyMatch(dimension -> "technicalDepth".equals(dimension.getId())));
        assertEquals("career-general-v1", rubrics.get(1).getId());
        assertFalse(rubrics.get(1).getEditable());
    }

    private CareerAdminService newService() {
        return new CareerAdminServiceImpl(
                resumeDocumentMapper,
                candidateProfileMapper,
                resumeVersionMapper,
                jobDescriptionMapper,
                jobAlignmentReportMapper,
                resumeOptimizationTaskMapper,
                resumeOptimizationReviewMapper,
                interviewSessionMapper,
                interviewReportMapper,
                careerSingleFlightRecordMapper,
                careerTaskAttemptMapper,
                resumeExportRecordMapper
        );
    }

    private ResumeDocumentDO resumeDocument() {
        return ResumeDocumentDO.builder()
                .id("resume-document-1")
                .userId("user-1")
                .parseStatus(CareerTaskStatus.SUCCESS.name())
                .rawText("resume raw text")
                .parseError(null)
                .traceId("trace-resume")
                .createTime(new Date(1000L))
                .updateTime(new Date(1000L))
                .build();
    }

    private JobAlignmentReportDO alignmentReport() {
        return JobAlignmentReportDO.builder()
                .id("alignment-1")
                .userId("user-1")
                .resumeVersionId("resume-version-1")
                .jdId("job-1")
                .score(87)
                .summary("alignment summary")
                .traceId("trace-alignment")
                .createTime(new Date(2000L))
                .updateTime(new Date(2000L))
                .build();
    }

    private ResumeOptimizationTaskDO optimizationTask() {
        return ResumeOptimizationTaskDO.builder()
                .id("task-1")
                .userId("user-1")
                .resumeVersionId("resume-version-1")
                .jdId("job-1")
                .status(CareerTaskStatus.SUCCESS.name())
                .outputJson("{\"summary\":\"optimization summary\"}")
                .traceId("trace-task")
                .createTime(new Date(3000L))
                .updateTime(new Date(3000L))
                .build();
    }

    private ResumeOptimizationReviewDO optimizationReview() {
        return ResumeOptimizationReviewDO.builder()
                .id("review-1")
                .taskId("task-1")
                .userId("user-1")
                .qualityScore(new BigDecimal("0.87"))
                .status(OptimizationReviewStatus.PASSED.name())
                .reviewerOutputJson("{\"riskSummary\":\"No truthfulness risk\"}")
                .traceId("trace-review")
                .createTime(new Date(3500L))
                .updateTime(new Date(3500L))
                .build();
    }

    private InterviewSessionDO interviewSession() {
        return InterviewSessionDO.builder()
                .id("session-1")
                .userId("user-1")
                .resumeVersionId("resume-version-1")
                .jdId("job-1")
                .status(InterviewSessionStatus.RUNNING.name())
                .currentTurnNo(3)
                .traceId("trace-session")
                .createTime(new Date(4000L))
                .updateTime(new Date(4000L))
                .build();
    }

    private InterviewReportDO interviewReport() {
        return InterviewReportDO.builder()
                .id("report-1")
                .sessionId("session-1")
                .userId("user-1")
                .overallScore(91)
                .summary("interview summary")
                .traceId("trace-report")
                .createTime(new Date(4500L))
                .updateTime(new Date(4500L))
                .build();
    }

    private ResumeExportRecordDO exportRecord() {
        return ResumeExportRecordDO.builder()
                .id("export-1")
                .userId("user-1")
                .resumeVersionId("resume-version-1")
                .exportType("PDF")
                .status("FAILED")
                .errorMessage("failed because of export error")
                .templateVersion("v1")
                .traceId("trace-export")
                .createTime(new Date(5000L))
                .updateTime(new Date(5000L))
                .build();
    }

    private CareerSingleFlightRecordDO singleFlightRecord() {
        return CareerSingleFlightRecordDO.builder()
                .id("single-flight-1")
                .singleFlightKey("career:single-flight")
                .scene("OPTIMIZATION")
                .ownerId("owner-1")
                .fencingToken(2L)
                .status("RUNNING")
                .requestCount(5)
                .traceId("trace-single-flight")
                .createTime(new Date(6000L))
                .updateTime(new Date(6000L))
                .build();
    }

    private CareerTaskAttemptDO taskAttempt() {
        return CareerTaskAttemptDO.builder()
                .id("attempt-1")
                .userId("user-1")
                .businessId("task-1")
                .scene("OPTIMIZATION_EXECUTOR")
                .idempotencyKey("OPTIMIZATION_EXECUTOR:user-1:task-1:prompt")
                .singleFlightKey("OPTIMIZATION_EXECUTOR:stable")
                .traceId("trace-attempt")
                .modelName("RagentModelRouter")
                .promptSummary("prompt summary")
                .status("SUCCESS")
                .replayed(false)
                .latencyMs(128L)
                .createTime(new Date(7000L))
                .updateTime(new Date(7000L))
                .build();
    }
}
