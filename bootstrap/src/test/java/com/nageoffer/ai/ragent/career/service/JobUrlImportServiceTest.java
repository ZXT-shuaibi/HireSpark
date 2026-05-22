package com.nageoffer.ai.ragent.career.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.career.controller.request.CareerJobUrlImportRequest;
import com.nageoffer.ai.ragent.career.crawler.CareerCrawlerProperties;
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
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                "\u004a\u0061\u0076\u0061 \u540e\u7aef\u5de5\u7a0b\u5e08",
                "\u793a\u4f8b\u79d1\u6280",
                """
                        \u5c97\u4f4d\u804c\u8d23\uff1a\u8d1f\u8d23 Spring Boot \u540e\u7aef\u670d\u52a1\u548c RAG Agent \u5e73\u53f0\u80fd\u529b\u5efa\u8bbe\uff0c\u63d0\u5347\u7cfb\u7edf\u7a33\u5b9a\u6027\u548c\u5de5\u7a0b\u6548\u7387\u3002
                        \u4efb\u804c\u8981\u6c42\uff1a\u719f\u6089 Java\u3001PostgreSQL\u3001Redis \u548c\u5206\u5e03\u5f0f\u670d\u52a1\u6cbb\u7406\uff0c\u6709\u826f\u597d\u7684\u4ee3\u7801\u8d28\u91cf\u610f\u8bc6\u3002
                        \u7ecf\u9a8c\u548c\u5b66\u5386\uff1a3 \u5e74\u4ee5\u4e0a\u5f00\u53d1\u7ecf\u9a8c\uff0c\u672c\u79d1\u53ca\u4ee5\u4e0a\u5b66\u5386\uff0c\u85aa\u8d44\u9762\u8bae\u3002
                        """));
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class))).thenReturn("{}");
        when(careerJsonParser.parseObject(anyString()))
                .thenReturn(Map.of("title", "\u004a\u0061\u0076\u0061 \u540e\u7aef\u5de5\u7a0b\u5e08"));
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

    @Test
    void rejectsCrawledUrlWhenContentDoesNotLookLikeJobDescription() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
        JobAlignmentReportMapper jobAlignmentReportMapper = mock(JobAlignmentReportMapper.class);
        ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
        CareerJsonParser careerJsonParser = mock(CareerJsonParser.class);
        CareerSingleFlightLlmService singleFlightLlmService = mock(CareerSingleFlightLlmService.class);
        CareerRetrievalEnhancementService enhancementService = mock(CareerRetrievalEnhancementService.class);
        JobPostingCrawler crawler = mock(JobPostingCrawler.class);
        when(crawler.crawl("https://www.baidu.com")).thenReturn(new JobPostingCrawlResult(
                "https://www.baidu.com",
                "Search Home",
                null,
                "Search news web images maps videos encyclopedia"));
        JobAlignmentServiceImpl service = new JobAlignmentServiceImpl(jobDescriptionMapper, jobAlignmentReportMapper,
                resumeVersionMapper, careerJsonParser, singleFlightLlmService, enhancementService);
        service.setJobPostingCrawler(crawler);
        CareerJobUrlImportRequest request = new CareerJobUrlImportRequest();
        request.setUrl("https://www.baidu.com");

        assertThatThrownBy(() -> service.importJobFromUrl(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("not look like a job description");
        verify(singleFlightLlmService, never()).chat(anyString(), anyString(), anyString(), any(ChatRequest.class));
        verify(jobDescriptionMapper, never()).insert(any(JobDescriptionDO.class));
    }

    @Test
    void rejectsCrawledUrlWhenLlmConfirmsItIsNotJobDescription() {
        UserContext.set(LoginUser.builder().userId("user-1").username("alice").build());
        JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
        JobAlignmentReportMapper jobAlignmentReportMapper = mock(JobAlignmentReportMapper.class);
        ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
        CareerJsonParser careerJsonParser = mock(CareerJsonParser.class);
        CareerSingleFlightLlmService singleFlightLlmService = mock(CareerSingleFlightLlmService.class);
        CareerRetrievalEnhancementService enhancementService = mock(CareerRetrievalEnhancementService.class);
        JobPostingCrawler crawler = mock(JobPostingCrawler.class);
        when(crawler.crawl("https://zhuanlan.zhihu.com/p/123")).thenReturn(new JobPostingCrawlResult(
                "https://zhuanlan.zhihu.com/p/123",
                "Article About Recruiting Systems",
                null,
                """
                        This article mentions job description, responsibilities, requirements,
                        qualifications, experience and education many times because it analyzes
                        how recruiting platforms write crawler examples. It is intentionally long
                        enough to pass the deterministic JD keyword rule, but the LLM verifier
                        should still reject it as not being an actual job posting.
                        """));
        when(singleFlightLlmService.chat(anyString(), anyString(), anyString(), any(ChatRequest.class)))
                .thenReturn("{\"isJd\":false}");
        when(careerJsonParser.parseObject("{\"isJd\":false}")).thenReturn(Map.of("isJd", false));
        CareerCrawlerProperties properties = new CareerCrawlerProperties();
        properties.getJdVerification().setLlmEnabled(true);
        JobAlignmentServiceImpl service = new JobAlignmentServiceImpl(jobDescriptionMapper, jobAlignmentReportMapper,
                resumeVersionMapper, careerJsonParser, singleFlightLlmService, enhancementService);
        service.setJobPostingCrawler(crawler);
        service.setCareerCrawlerProperties(properties);
        CareerJobUrlImportRequest request = new CareerJobUrlImportRequest();
        request.setUrl("https://zhuanlan.zhihu.com/p/123");

        assertThatThrownBy(() -> service.importJobFromUrl(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("confirmed as non-JD");
        verify(jobDescriptionMapper, never()).insert(any(JobDescriptionDO.class));
    }
}
