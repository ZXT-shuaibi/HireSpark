package com.nageoffer.ai.ragent.admin.service.dashboard;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DashboardMetricCalculator {

    public Double calcPct(long current, long prev) {
        if (prev <= 0) {
            return null;
        }
        return round1(((current - prev) * 100.0) / prev);
    }

    public long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (Long value : values) {
            sum += value;
        }
        return Math.round(sum / (double) values.size());
    }

    public long percentile95(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    public double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
