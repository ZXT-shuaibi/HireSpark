package com.nageoffer.ai.ragent.career.service.export;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.core.export.excel.EasyExcelWorkbookExportAgent;
import com.nageoffer.ai.ragent.core.export.excel.ExcelSheetData;
import com.nageoffer.ai.ragent.core.export.excel.ExcelWorkbookExportAgent;
import com.nageoffer.ai.ragent.core.export.excel.ExcelWorkbookRequest;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EasyExcelCareerExcelExportAgent implements CareerExcelExportAgent {

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExcelWorkbookExportAgent workbookExportAgent;

    public EasyExcelCareerExcelExportAgent() {
        this(new EasyExcelWorkbookExportAgent());
    }

    @Autowired
    public EasyExcelCareerExcelExportAgent(ExcelWorkbookExportAgent workbookExportAgent) {
        this.workbookExportAgent = workbookExportAgent == null
                ? new EasyExcelWorkbookExportAgent()
                : workbookExportAgent;
    }

    @Override
    public CareerExcelExportResult exportAlignmentReport(JobAlignmentReportDO report) {
        if (report == null) {
            throw new ServiceException("Alignment report is required for Excel export");
        }
        byte[] content = writeWorkbook(List.of(
                sheet("Summary", summaryRows(alignmentSummary(report)), summaryHead()),
                sheet("Evidence", jsonRows(report.getEvidenceJson()), itemHead()),
                sheet("Gaps", jsonRows(report.getGapsJson()), itemHead()),
                sheet("Risks", jsonRows(report.getRisksJson()), itemHead())
        ));
        return new CareerExcelExportResult(
                "career-alignment-report-" + safeToken(report.getId()) + ".xlsx",
                CareerExcelExportResult.XLSX_CONTENT_TYPE,
                content);
    }

    @Override
    public CareerExcelExportResult exportInterviewReport(InterviewReportDO report) {
        if (report == null) {
            throw new ServiceException("Interview report is required for Excel export");
        }
        byte[] content = writeWorkbook(List.of(
                sheet("Summary", summaryRows(interviewSummary(report)), summaryHead()),
                sheet("Radar", jsonRows(report.getRadarJson()), itemHead()),
                sheet("Playback", jsonRows(report.getPlaybackJson()), itemHead()),
                sheet("Suggestions", jsonRows(report.getSuggestionsJson()), itemHead())
        ));
        return new CareerExcelExportResult(
                "career-interview-report-" + safeToken(report.getId()) + ".xlsx",
                CareerExcelExportResult.XLSX_CONTENT_TYPE,
                content);
    }

    private byte[] writeWorkbook(List<ExcelSheetData> sheets) {
        try {
            return workbookExportAgent.export(new ExcelWorkbookRequest("career-report.xlsx", sheets)).content();
        } catch (Exception ex) {
            throw new ServiceException("Failed to export career report Excel: " + ex.getMessage());
        }
    }

    private Map<String, String> alignmentSummary(JobAlignmentReportDO report) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Report ID", report.getId());
        values.put("Resume Version ID", report.getResumeVersionId());
        values.put("Job Description ID", report.getJdId());
        values.put("Score", stringValue(report.getScore()));
        values.put("Summary", report.getSummary());
        values.put("Trace ID", report.getTraceId());
        return values;
    }

    private Map<String, String> interviewSummary(InterviewReportDO report) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Report ID", report.getId());
        values.put("Session ID", report.getSessionId());
        values.put("Overall Score", stringValue(report.getOverallScore()));
        values.put("Summary", report.getSummary());
        values.put("Trace ID", report.getTraceId());
        return values;
    }

    private List<List<String>> summaryRows(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> List.of(entry.getKey(), StrUtil.blankToDefault(entry.getValue(), "")))
                .toList();
    }

    private List<List<String>> jsonRows(String json) {
        List<Object> values = readJsonList(json);
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            int rowNo = i + 1;
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    rows.add(List.of(
                            String.valueOf(rowNo),
                            String.valueOf(entry.getKey()),
                            compactValue(entry.getValue())));
                }
            } else {
                rows.add(List.of(String.valueOf(rowNo), "", compactValue(value)));
            }
        }
        return rows;
    }

    private List<Object> readJsonList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return List.of();
            }
            if (node.isArray()) {
                return objectMapper.convertValue(node, LIST_TYPE);
            }
            return List.of(objectMapper.convertValue(node, Object.class));
        } catch (Exception ex) {
            throw new ServiceException("Failed to parse career report JSON for Excel export");
        }
    }

    private String compactValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private List<List<String>> summaryHead() {
        return List.of(List.of("Field"), List.of("Value"));
    }

    private List<List<String>> itemHead() {
        return List.of(List.of("Index"), List.of("Key"), List.of("Value"));
    }

    private ExcelSheetData sheet(String name, List<List<String>> rows, List<List<String>> head) {
        return new ExcelSheetData(name, head, rows);
    }

    private String safeToken(String value) {
        String token = StrUtil.blankToDefault(value, "unknown").trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-");
        return StrUtil.blankToDefault(token, "unknown");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
