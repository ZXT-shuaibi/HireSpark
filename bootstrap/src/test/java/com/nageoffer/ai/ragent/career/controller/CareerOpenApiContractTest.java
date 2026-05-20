package com.nageoffer.ai.ragent.career.controller;

import com.nageoffer.ai.ragent.career.controller.admin.CareerAdminController;
import com.nageoffer.ai.ragent.career.config.OpenApiDocumentationConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CareerOpenApiContractTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            CareerResumeController.class,
            CareerJobController.class,
            CareerOptimizationController.class,
            CareerInterviewController.class,
            CareerAdminController.class
    );

    @Test
    @DisplayName("Career OpenAPI exposes user, admin, runtime and export groups")
    void careerOpenApiDeclaresMainDeliveryGroups() {
        OpenApiDocumentationConfig config = new OpenApiDocumentationConfig();

        assertThat(List.of(
                config.careerUserApi(),
                config.careerAdminApi(),
                config.careerRuntimeApi(),
                config.careerExportApi()
        )).extracting(GroupedOpenApi::getGroup)
                .containsExactly("career-user", "career-admin", "career-runtime", "career-export");
    }

    @Test
    void careerControllersDeclareOpenApiTags() {
        assertThat(CONTROLLERS)
                .allSatisfy(controller -> assertThat(controller.getAnnotation(Tag.class))
                        .as(controller.getSimpleName() + " should declare OpenAPI tag")
                        .isNotNull());
    }

    @Test
    void careerMappedMethodsDeclareOpenApiOperations() {
        for (Class<?> controller : CONTROLLERS) {
            assertThat(mappedMethods(controller))
                    .allSatisfy(method -> assertThat(method.getAnnotation(Operation.class))
                            .as(controller.getSimpleName() + "#" + method.getName() + " should declare OpenAPI operation")
                            .isNotNull());
        }
    }

    private List<Method> mappedMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(this::isMappedMethod)
                .toList();
    }

    private boolean isMappedMethod(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
