package com.nageoffer.ai.ragent.core.export.excel;

import java.util.List;

public record ExcelWorkbookRequest(String fileName,
                                   List<ExcelSheetData> sheets) {
}
