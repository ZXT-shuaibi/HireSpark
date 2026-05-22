package com.nageoffer.ai.ragent.core.datasource;

import cn.hutool.core.util.StrUtil;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private final String primaryDataSourceName;

    public DynamicRoutingDataSource(String primaryDataSourceName) {
        this.primaryDataSourceName = StrUtil.blankToDefault(primaryDataSourceName, "primary");
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return RoutingDataSourceContext.peek().orElse(primaryDataSourceName);
    }
}
