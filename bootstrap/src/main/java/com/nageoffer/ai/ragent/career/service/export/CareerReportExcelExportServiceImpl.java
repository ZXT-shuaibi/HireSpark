package com.nageoffer.ai.ragent.career.service.export;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.career.controller.vo.CareerReportExcelExportVO;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.mapper.InterviewReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CareerReportExcelExportServiceImpl implements CareerReportExcelExportService {

    private static final String REPORT_TYPE_ALIGNMENT = "ALIGNMENT";
    private static final String REPORT_TYPE_INTERVIEW = "INTERVIEW";

    private final JobAlignmentReportMapper jobAlignmentReportMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final CareerExcelExportAgent excelExportAgent;
    private final FileStorageService fileStorageService;

    @Value("${career.storage.excel-export-bucket:career-report-export}")
    private String excelExportBucketName = "career-report-export";

    @Override
    public CareerReportExcelExportVO exportAlignmentReport(String reportId) {
        String userId = requireUserId();
        if (StrUtil.isBlank(reportId)) {
            throw new ClientException("Alignment report id is required");
        }
        JobAlignmentReportDO report = jobAlignmentReportMapper.selectOne(
                Wrappers.lambdaQuery(JobAlignmentReportDO.class)
                        .eq(JobAlignmentReportDO::getId, reportId)
                        .eq(JobAlignmentReportDO::getUserId, userId)
                        .eq(JobAlignmentReportDO::getDeleted, 0));
        if (report == null) {
            throw new ClientException("Alignment report does not exist");
        }
        CareerExcelExportResult export = excelExportAgent.exportAlignmentReport(report);
        StoredFileDTO stored = fileStorageService.upload(
                excelExportBucketName,
                export.content(),
                export.fileName(),
                export.contentType());
        return CareerReportExcelExportVO.builder()
                .reportType(REPORT_TYPE_ALIGNMENT)
                .reportId(report.getId())
                .fileName(export.fileName())
                .fileUrl(stored == null ? null : stored.getUrl())
                .contentType(export.contentType())
                .traceId(report.getTraceId())
                .build();
    }

    @Override
    public CareerReportExcelExportVO exportInterviewReport(String sessionId) {
        String userId = requireUserId();
        if (StrUtil.isBlank(sessionId)) {
            throw new ClientException("Interview session id is required");
        }
        InterviewReportDO report = interviewReportMapper.selectOne(
                Wrappers.lambdaQuery(InterviewReportDO.class)
                        .eq(InterviewReportDO::getSessionId, sessionId)
                        .eq(InterviewReportDO::getUserId, userId)
                        .eq(InterviewReportDO::getDeleted, 0)
                        .orderByDesc(InterviewReportDO::getCreateTime)
                        .last("LIMIT 1"));
        if (report == null) {
            throw new ClientException("Interview report does not exist");
        }
        CareerExcelExportResult export = excelExportAgent.exportInterviewReport(report);
        StoredFileDTO stored = fileStorageService.upload(
                excelExportBucketName,
                export.content(),
                export.fileName(),
                export.contentType());
        return CareerReportExcelExportVO.builder()
                .reportType(REPORT_TYPE_INTERVIEW)
                .reportId(report.getId())
                .sessionId(report.getSessionId())
                .fileName(export.fileName())
                .fileUrl(stored == null ? null : stored.getUrl())
                .contentType(export.contentType())
                .traceId(report.getTraceId())
                .build();
    }

    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("User information is missing");
        }
        return userId;
    }
}
