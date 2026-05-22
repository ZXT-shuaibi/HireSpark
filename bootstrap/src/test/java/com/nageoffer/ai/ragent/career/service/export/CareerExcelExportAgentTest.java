package com.nageoffer.ai.ragent.career.service.export;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CareerExcelExportAgentTest {

    @Test
    void exportsAlignmentReportWorkbookWithBusinessSheets() {
        CareerExcelExportAgent agent = new EasyExcelCareerExcelExportAgent();
        JobAlignmentReportDO report = JobAlignmentReportDO.builder()
                .id("report-1")
                .resumeVersionId("resume-1")
                .jdId("jd-1")
                .score(86)
                .summary("strong backend match")
                .evidenceJson("""
                        [
                          {"skill":"Java","detail":"Spring Boot"},
                          {"skill":"Redis","detail":"cache design"}
                        ]
                        """)
                .gapsJson("""
                        [{"name":"Kubernetes","severity":"medium"}]
                        """)
                .risksJson("""
                        [{"name":"domain depth","severity":"low"}]
                        """)
                .traceId("trace-1")
                .build();

        CareerExcelExportResult result = agent.exportAlignmentReport(report);

        assertThat(result.fileName()).isEqualTo("career-alignment-report-report-1.xlsx");
        assertThat(result.contentType()).isEqualTo(CareerExcelExportResult.XLSX_CONTENT_TYPE);
        assertThat(result.content()).hasSizeGreaterThan(512);
        assertThat(new String(result.content(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(readSheet(result.content(), "Summary"))
                .contains(List.of("Report ID", "report-1"))
                .contains(List.of("Score", "86"))
                .contains(List.of("Trace ID", "trace-1"));
        assertThat(readSheet(result.content(), "Evidence"))
                .contains(List.of("1", "skill", "Java"))
                .contains(List.of("2", "detail", "cache design"));
        assertThat(readSheet(result.content(), "Risks"))
                .contains(List.of("1", "severity", "low"));
    }

    @Test
    void exportsInterviewReportWorkbookWithRadarAndSuggestionSheets() {
        CareerExcelExportAgent agent = new EasyExcelCareerExcelExportAgent();
        InterviewReportDO report = InterviewReportDO.builder()
                .id("interview-report-1")
                .sessionId("session-1")
                .overallScore(78)
                .summary("communication is clear")
                .radarJson("""
                        [{"dimension":"architecture","score":82}]
                        """)
                .playbackJson("""
                        [{"turnNo":1,"note":"good STAR structure"}]
                        """)
                .suggestionsJson("""
                        [{"title":"Add metrics","priority":"high"}]
                        """)
                .traceId("trace-interview-1")
                .build();

        CareerExcelExportResult result = agent.exportInterviewReport(report);

        assertThat(result.fileName()).isEqualTo("career-interview-report-interview-report-1.xlsx");
        assertThat(result.contentType()).isEqualTo(CareerExcelExportResult.XLSX_CONTENT_TYPE);
        assertThat(readSheet(result.content(), "Summary"))
                .contains(List.of("Report ID", "interview-report-1"))
                .contains(List.of("Overall Score", "78"));
        assertThat(readSheet(result.content(), "Radar"))
                .contains(List.of("1", "dimension", "architecture"))
                .contains(List.of("1", "score", "82"));
        assertThat(readSheet(result.content(), "Suggestions"))
                .contains(List.of("1", "priority", "high"));
    }

    private List<List<String>> readSheet(byte[] content, String sheetName) {
        List<List<String>> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(content), new AnalysisEventListener<Map<Integer, String>>() {

            @Override
            public void invoke(Map<Integer, String> row, AnalysisContext context) {
                int lastIndex = row.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                List<String> values = new ArrayList<>();
                for (int index = 0; index <= lastIndex; index++) {
                    values.add(row.get(index));
                }
                rows.add(values);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet(sheetName).headRowNumber(1).doRead();
        return rows;
    }
}
