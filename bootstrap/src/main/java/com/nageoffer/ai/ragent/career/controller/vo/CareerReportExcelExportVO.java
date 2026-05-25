package com.nageoffer.ai.ragent.career.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerReportExcelExportVO {

    private String reportType;

    private String reportId;

    private String sessionId;

    private String fileName;

    private String fileUrl;

    private String contentType;

    private String traceId;
}
