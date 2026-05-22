package com.nageoffer.ai.ragent.career.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.request.CareerJobUrlImportRequest;
import com.nageoffer.ai.ragent.career.crawler.JobPostingCrawlResult;
import com.nageoffer.ai.ragent.career.crawler.JobPostingCrawler;
import com.nageoffer.ai.ragent.career.dao.entity.JobAlignmentReportDO;
import com.nageoffer.ai.ragent.career.dao.entity.JobDescriptionDO;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.career.dao.mapper.JobAlignmentReportMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.JobDescriptionMapper;
import com.nageoffer.ai.ragent.career.dao.mapper.ResumeVersionMapper;
import com.nageoffer.ai.ragent.career.service.impl.JobAlignmentServiceImpl;
import com.nageoffer.ai.ragent.career.service.parser.CareerJsonParser;
import com.nageoffer.ai.ragent.career.service.retrieval.CareerRetrievalEnhancementService;
import com.nageoffer.ai.ragent.career.service.singleflight.CareerSingleFlightLlmService;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobUrlImportServiceTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @BeforeAll
    static void initMyBatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobDescriptionDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobAlignmentReportDO.class);
    }

    @Test
    void importsUrlByCrawlingThenReusingJobCreationFlow() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
        JobAlignmentReportMapper jobAlignmentReportMapper = mock(JobAlignmentReportMapper.class);
        ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
        CareerJsonParser careerJsonParser = mock(CareerJsonParser.class);
        CareerSingleFlightLlmService singleFlightLlmService = mock(CareerSingleFlightLlmService.class);
        CareerRetrievalEnhancementService enhancementService = mock(CareerRetrievalEnhancementService.class);
        JobPostingCrawler crawler = mock(JobPostingCrawler.class);
        when(crawler.crawl("https://jobs.example/java")).thenReturn(new JobPostingCrawlResult(
                "https://jobs.example/java",
                "Java 后端工程师",
                "星河科技",
                "岗位职责：负责 Java 后端服务和 RAG Agent 平台建设。任职要求：熟悉 Spring Boot、PostgreSQL、Redis。"));
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class))).thenReturn("{}");
        when(careerJsonParser.parseObject(anyString())).thenReturn(Map.of("title", "Java 后端工程师"));
        JobAlignmentServiceImpl service = new JobAlignmentServiceImpl(jobDescriptionMapper, jobAlignmentReportMapper,
                resumeVersionMapper, careerJsonParser, singleFlightLlmService, enhancementService);
        service.setJobPostingCrawler(crawler);
        CareerJobUrlImportRequest request = new CareerJobUrlImportRequest();
        request.setUrl("https://jobs.example/java");

        service.importJobFromUrl(request);

        verify(crawler).crawl("https://jobs.example/java");
        var captor = org.mockito.ArgumentCaptor.forClass(JobDescriptionDO.class);
        verify(jobDescriptionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("URL");
        assertThat(captor.getValue().getSourceLocation()).isEqualTo("https://jobs.example/java");
        assertThat(captor.getValue().getRawText()).contains("RAG Agent");
    }
}
