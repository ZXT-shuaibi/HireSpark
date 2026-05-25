package com.nageoffer.ai.ragent.career.service.export;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nageoffer.ai.ragent.career.controller.vo.CareerReportExcelExportVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerReportExcelExportServiceTest {

    @Mock
    private JobAlignmentReportMapper jobAlignmentReportMapper;

    @Mock
    private InterviewReportMapper interviewReportMapper;

    @Mock
    private CareerExcelExportAgent excelExportAgent;

    @Mock
    private FileStorageService fileStorageService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void exportsAlignmentReportExcelThroughStorage() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        JobAlignmentReportDO report = JobAlignmentReportDO.builder()
                .id("report-1")
                .userId("user-1")
                .traceId("trace-1")
                .build();
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(jobAlignmentReportMapper.selectOne(anyAlignmentWrapper())).thenReturn(report);
        when(excelExportAgent.exportAlignmentReport(report)).thenReturn(new CareerExcelExportResult(
                "alignment.xlsx",
                CareerExcelExportResult.XLSX_CONTENT_TYPE,
                content));
        when(fileStorageService.upload(eq("career-report-export"), any(byte[].class),
                eq("alignment.xlsx"), eq(CareerExcelExportResult.XLSX_CONTENT_TYPE)))
                .thenReturn(StoredFileDTO.builder().url("s3://career-report-export/alignment.xlsx").build());

        CareerReportExcelExportVO result = newService().exportAlignmentReport("report-1");

        assertThat(result.getReportType()).isEqualTo("ALIGNMENT");
        assertThat(result.getReportId()).isEqualTo("report-1");
        assertThat(result.getTraceId()).isEqualTo("trace-1");
        assertThat(result.getFileName()).isEqualTo("alignment.xlsx");
        assertThat(result.getFileUrl()).isEqualTo("s3://career-report-export/alignment.xlsx");
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).upload(eq("career-report-export"), contentCaptor.capture(),
                eq("alignment.xlsx"), eq(CareerExcelExportResult.XLSX_CONTENT_TYPE));
        assertThat(contentCaptor.getValue()).isEqualTo(content);
    }

    @Test
    void exportsInterviewReportExcelBySession() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        InterviewReportDO report = InterviewReportDO.builder()
                .id("interview-report-1")
                .sessionId("session-1")
                .userId("user-1")
                .traceId("trace-interview-1")
                .build();
        when(interviewReportMapper.selectOne(anyInterviewWrapper())).thenReturn(report);
        when(excelExportAgent.exportInterviewReport(report)).thenReturn(new CareerExcelExportResult(
                "interview.xlsx",
                CareerExcelExportResult.XLSX_CONTENT_TYPE,
                new byte[]{1, 2, 3}));
        when(fileStorageService.upload(eq("career-report-export"), any(byte[].class),
                eq("interview.xlsx"), eq(CareerExcelExportResult.XLSX_CONTENT_TYPE)))
                .thenReturn(StoredFileDTO.builder().url("s3://career-report-export/interview.xlsx").build());

        CareerReportExcelExportVO result = newService().exportInterviewReport("session-1");

        assertThat(result.getReportType()).isEqualTo("INTERVIEW");
        assertThat(result.getReportId()).isEqualTo("interview-report-1");
        assertThat(result.getSessionId()).isEqualTo("session-1");
        assertThat(result.getTraceId()).isEqualTo("trace-interview-1");
        assertThat(result.getFileUrl()).isEqualTo("s3://career-report-export/interview.xlsx");
    }

    private CareerReportExcelExportServiceImpl newService() {
        return new CareerReportExcelExportServiceImpl(
                jobAlignmentReportMapper,
                interviewReportMapper,
                excelExportAgent,
                fileStorageService);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<JobAlignmentReportDO> anyAlignmentWrapper() {
        return (Wrapper<JobAlignmentReportDO>) any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<InterviewReportDO> anyInterviewWrapper() {
        return (Wrapper<InterviewReportDO>) any(Wrapper.class);
    }
}
