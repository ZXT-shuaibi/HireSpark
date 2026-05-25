package com.nageoffer.ai.ragent.core.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingDataSourceContextTest {

    @AfterEach
    void tearDown() {
        RoutingDataSourceContext.clear();
    }

    @Test
    void supportsNestedDataSourceSwitching() {
        assertThat(RoutingDataSourceContext.peek()).isEmpty();

        RoutingDataSourceContext.push("primary");
        assertThat(RoutingDataSourceContext.peek()).hasValue("primary");

        RoutingDataSourceContext.push("analytics");
        assertThat(RoutingDataSourceContext.peek()).hasValue("analytics");

        RoutingDataSourceContext.pop();
        assertThat(RoutingDataSourceContext.peek()).hasValue("primary");

        RoutingDataSourceContext.pop();
        assertThat(RoutingDataSourceContext.peek()).isEmpty();
    }

    @Test
    void ignoresBlankDataSourceNames() {
        RoutingDataSourceContext.push(" ");

        assertThat(RoutingDataSourceContext.peek()).isEmpty();
    }
}
