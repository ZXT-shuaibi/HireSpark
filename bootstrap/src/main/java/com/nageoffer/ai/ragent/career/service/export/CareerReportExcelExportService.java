package com.nageoffer.ai.ragent.career.service.export;

import com.nageoffer.ai.ragent.career.controller.vo.CareerReportExcelExportVO;

public interface CareerReportExcelExportService {

    CareerReportExcelExportVO exportAlignmentReport(String reportId);

    CareerReportExcelExportVO exportInterviewReport(String sessionId);
}
