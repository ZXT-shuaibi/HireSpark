package com.nageoffer.ai.ragent.core.export.excel;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class EasyExcelWorkbookExportAgent implements ExcelWorkbookExportAgent {

    @Override
    public ExcelWorkbookResult export(ExcelWorkbookRequest request) {
        if (request == null || request.sheets() == null || request.sheets().isEmpty()) {
            throw new ServiceException("Excel workbook sheets are required");
        }
        String fileName = normalizeFileName(request.fileName());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(outputStream).autoCloseStream(false).build()) {
            for (int index = 0; index < request.sheets().size(); index++) {
                ExcelSheetData sheet = request.sheets().get(index);
                WriteSheet writeSheet = EasyExcel.writerSheet(index, normalizeSheetName(sheet.name(), index))
                        .head(sheet.head() == null ? List.of() : sheet.head())
                        .build();
                writer.write(sheet.rows() == null ? List.of() : sheet.rows(), writeSheet);
            }
            writer.finish();
            return new ExcelWorkbookResult(fileName, ExcelWorkbookResult.XLSX_CONTENT_TYPE, outputStream.toByteArray());
        } catch (Exception ex) {
            throw new ServiceException("Failed to export Excel workbook: " + ex.getMessage());
        }
    }

    private String normalizeFileName(String fileName) {
        String value = StrUtil.blankToDefault(fileName, "export.xlsx").trim();
        return value.toLowerCase().endsWith(".xlsx") ? value : value + ".xlsx";
    }

    private String normalizeSheetName(String sheetName, int index) {
        String value = StrUtil.blankToDefault(sheetName, "Sheet" + (index + 1)).trim()
                .replaceAll("[\\\\/?*\\[\\]:]", "-");
        if (value.length() > 31) {
            return value.substring(0, 31);
        }
        return StrUtil.blankToDefault(value, "Sheet" + (index + 1));
    }
}
