package com.nageoffer.ai.ragent.career.controller;

import com.nageoffer.ai.ragent.career.controller.vo.CareerReportExcelExportVO;
import com.nageoffer.ai.ragent.career.service.export.CareerReportExcelExportService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Career Excel Export", description = "Career report Excel export endpoints")
public class CareerExcelExportController {

    private final CareerReportExcelExportService careerReportExcelExportService;

    @PostMapping("/career/alignments/{reportId}/excel-export")
    @Operation(summary = "Export alignment report as Excel")
    public Result<CareerReportExcelExportVO> exportAlignmentReport(@PathVariable String reportId) {
        return Results.success(careerReportExcelExportService.exportAlignmentReport(reportId));
    }

    @PostMapping("/career/interviews/{sessionId}/report/excel-export")
    @Operation(summary = "Export interview report as Excel")
    public Result<CareerReportExcelExportVO> exportInterviewReport(@PathVariable String sessionId) {
        return Results.success(careerReportExcelExportService.exportInterviewReport(sessionId));
    }
}
