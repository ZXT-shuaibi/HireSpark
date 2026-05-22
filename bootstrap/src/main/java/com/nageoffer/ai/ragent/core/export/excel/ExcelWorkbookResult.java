package com.nageoffer.ai.ragent.core.export.excel;

public record ExcelWorkbookResult(String fileName,
                                  String contentType,
                                  byte[] content) {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
}
