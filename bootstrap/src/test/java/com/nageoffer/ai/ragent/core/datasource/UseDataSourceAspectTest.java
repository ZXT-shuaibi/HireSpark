package com.nageoffer.ai.ragent.core.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UseDataSourceAspectTest {

    @AfterEach
    void tearDown() {
        RoutingDataSourceContext.clear();
    }

    @Test
    void annotationSwitchesDataSourceOnlyInsideMethod() {
        DemoService service = proxy(new DemoService());

        assertThat(service.currentDataSource()).isEqualTo("reporting");
        assertThat(RoutingDataSourceContext.peek()).isEmpty();
    }

    @Test
    void clearsDataSourceAfterException() {
        DemoService service = proxy(new DemoService());

        assertThatThrownBy(service::failInsideReporting)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom:reporting");
        assertThat(RoutingDataSourceContext.peek()).isEmpty();
    }

    private DemoService proxy(DemoService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new UseDataSourceAspect());
        return factory.getProxy();
    }

    static class DemoService {

        @UseDataSource("reporting")
        String currentDataSource() {
            return RoutingDataSourceContext.peek().orElse("none");
        }

        @UseDataSource("reporting")
        void failInsideReporting() {
            throw new IllegalStateException("boom:" + RoutingDataSourceContext.peek().orElse("none"));
        }
    }
}
