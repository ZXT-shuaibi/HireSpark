package com.nageoffer.ai.ragent.admin.service.dashboard;

import com.nageoffer.ai.ragent.admin.controller.vo.DashboardTrendPointVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DashboardTrendPointFactory {

    public <T> List<DashboardTrendPointVO> longPoints(DashboardTimeBucket<T> bucket, Map<T, Long> values) {
        List<DashboardTrendPointVO> points = new ArrayList<>();
        for (T key : bucket.keys()) {
            long value = values.getOrDefault(key, 0L);
            points.add(DashboardTrendPointVO.builder()
                    .ts(bucket.timestampExtractor().apply(key))
                    .value((double) value)
                    .build());
        }
        return points;
    }

    public <T> List<DashboardTrendPointVO> doublePoints(DashboardTimeBucket<T> bucket, Map<T, Double> values) {
        List<DashboardTrendPointVO> points = new ArrayList<>();
        for (T key : bucket.keys()) {
            double value = values.getOrDefault(key, 0.0);
            points.add(DashboardTrendPointVO.builder()
                    .ts(bucket.timestampExtractor().apply(key))
                    .value(value)
                    .build());
        }
        return points;
    }
}
