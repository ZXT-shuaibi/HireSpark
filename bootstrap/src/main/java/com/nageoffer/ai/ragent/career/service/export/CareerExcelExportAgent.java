package com.nageoffer.ai.ragent.career.service.export;

import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;

public interface CareerExcelExportAgent {

    CareerExcelExportResult exportAlignmentReport(JobAlignmentReportDO report);

    CareerExcelExportResult exportInterviewReport(InterviewReportDO report);
}
