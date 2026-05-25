package com.nageoffer.ai.ragent.admin.service.dashboard;

import com.nageoffer.ai.ragent.admin.controller.vo.DashboardTrendPointVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardTrendPointFactoryTest {

    private final DashboardTrendPointFactory pointFactory = new DashboardTrendPointFactory();

    @Test
    void buildsDenseDayPointsWithZeroFilledMissingValues() {
        ZoneId zoneId = ZoneId.of("UTC");
        DashboardTimeBucket<LocalDate> bucket = DashboardTimeBucket.days(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 4),
                zoneId);

        List<DashboardTrendPointVO> points = pointFactory.longPoints(bucket, Map.of(
                LocalDate.of(2026, 5, 2), 5L));

        assertThat(points).hasSize(3);
        assertThat(points).extracting(DashboardTrendPointVO::getValue)
                .containsExactly(0.0, 5.0, 0.0);
        assertThat(points.get(0).getTs()).isEqualTo(
                LocalDate.of(2026, 5, 1).atStartOfDay(zoneId).toInstant().toEpochMilli());
    }
}
