package com.nageoffer.ai.ragent.core.export.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EasyExcelWorkbookExportAgentTest {

    @Test
    void exportsGenericWorkbookWithMultipleSheets() {
        ExcelWorkbookExportAgent agent = new EasyExcelWorkbookExportAgent();
        ExcelWorkbookRequest request = new ExcelWorkbookRequest("audit-report.xlsx", List.of(
                new ExcelSheetData("Users",
                        List.of(List.of("User ID"), List.of("Phone")),
                        List.of(List.of("u-1", "138****0000"))),
                new ExcelSheetData("Audit",
                        List.of(List.of("Action"), List.of("Result")),
                        List.of(List.of("LOGIN", "SUCCESS")))
        ));

        ExcelWorkbookResult result = agent.export(request);

        assertThat(result.fileName()).isEqualTo("audit-report.xlsx");
        assertThat(result.contentType()).isEqualTo(ExcelWorkbookResult.XLSX_CONTENT_TYPE);
        assertThat(result.content()).isNotEmpty();
        assertThat(readFirstSheet(result.content())).contains(List.of("u-1", "138****0000"));
    }

    private List<List<String>> readFirstSheet(byte[] content) {
        List<List<String>> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(content), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                rows.add(new ArrayList<>(data.values()));
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet(0).doRead();
        return rows;
    }
}
