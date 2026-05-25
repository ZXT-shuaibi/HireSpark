package com.nageoffer.ai.ragent.career.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiDocumentationConfig {

    @Bean
    public OpenAPI ragentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ragent Career API")
                        .version("v1")
                        .description("Career agent APIs for resumes, jobs, optimization, interviews, reports, and admin tracing."));
    }

    @Bean
    public GroupedOpenApi careerUserApi() {
        return GroupedOpenApi.builder()
                .group("career-user")
                .pathsToMatch("/career/**")
                .build();
    }

    @Bean
    public GroupedOpenApi careerAdminApi() {
        return GroupedOpenApi.builder()
                .group("career-admin")
                .pathsToMatch("/admin/career/**")
                .build();
    }

    @Bean
    public GroupedOpenApi careerRuntimeApi() {
        return GroupedOpenApi.builder()
                .group("career-runtime")
                .pathsToMatch(
                        "/career/interviews/**",
                        "/career/optimizations/*/progress/stream"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi careerExportApi() {
        return GroupedOpenApi.builder()
                .group("career-export")
                .pathsToMatch("/career/resumes/export")
                .build();
    }
}
