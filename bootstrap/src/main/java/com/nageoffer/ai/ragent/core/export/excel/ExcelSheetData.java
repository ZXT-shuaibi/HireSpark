package com.nageoffer.ai.ragent.core.export.excel;

import java.util.List;

public record ExcelSheetData(String name,
                             List<List<String>> head,
                             List<List<String>> rows) {
}
