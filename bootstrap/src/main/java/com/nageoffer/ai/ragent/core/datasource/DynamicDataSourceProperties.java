package com.nageoffer.ai.ragent.core.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "ragent.datasource.dynamic")
public class DynamicDataSourceProperties {

    private boolean enabled = false;

    private String primary = "primary";

    private boolean strict = false;

    private Map<String, DataSourceItem> dataSources = new LinkedHashMap<>();

    @Data
    public static class DataSourceItem {

        private String driverClassName;

        private String url;

        private String username;

        private String password;
    }
}
