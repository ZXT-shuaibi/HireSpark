package com.nageoffer.ai.ragent.career.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import static org.assertj.core.api.Assertions.assertThat;

class CareerExcelExportControllerMappingTest {

    @Test
    void exposesCareerReportExcelExportEndpoints() throws NoSuchMethodException {
        PostMapping alignmentMapping = CareerExcelExportController.class
                .getMethod("exportAlignmentReport", String.class)
                .getAnnotation(PostMapping.class);
        PostMapping interviewMapping = CareerExcelExportController.class
                .getMethod("exportInterviewReport", String.class)
                .getAnnotation(PostMapping.class);

        assertThat(alignmentMapping.value()).containsExactly("/career/alignments/{reportId}/excel-export");
        assertThat(interviewMapping.value()).containsExactly("/career/interviews/{sessionId}/report/excel-export");
    }
}
