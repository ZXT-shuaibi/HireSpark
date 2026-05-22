package com.nageoffer.ai.ragent.core.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicRoutingDataSourceTest {

    @AfterEach
    void tearDown() {
        RoutingDataSourceContext.clear();
    }

    @Test
    void usesPrimaryWhenNoDataSourceIsSelected() {
        InspectableDynamicRoutingDataSource dataSource = new InspectableDynamicRoutingDataSource("primary");

        assertThat(dataSource.currentLookupKey()).isEqualTo("primary");
    }

    @Test
    void usesContextDataSourceWhenSelected() {
        InspectableDynamicRoutingDataSource dataSource = new InspectableDynamicRoutingDataSource("primary");
        RoutingDataSourceContext.push("analytics");

        assertThat(dataSource.currentLookupKey()).isEqualTo("analytics");
    }

    static class InspectableDynamicRoutingDataSource extends DynamicRoutingDataSource {

        InspectableDynamicRoutingDataSource(String primaryDataSourceName) {
            super(primaryDataSourceName);
        }

        Object currentLookupKey() {
            return determineCurrentLookupKey();
        }
    }
}
