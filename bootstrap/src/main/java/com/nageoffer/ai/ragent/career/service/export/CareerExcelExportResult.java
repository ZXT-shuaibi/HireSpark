package com.nageoffer.ai.ragent.career.service.export;

public record CareerExcelExportResult(String fileName, String contentType, byte[] content) {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
}
