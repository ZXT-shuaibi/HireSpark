package com.nageoffer.ai.ragent.admin.service.dashboard;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardWindowResolverTest {

    private final DashboardWindowResolver resolver = new DashboardWindowResolver();

    @Test
    void parseWindowFallsBackWhenInputIsBlankOrInvalid() {
        Duration fallback = Duration.ofHours(24);

        assertThat(resolver.parseWindow(null, fallback)).isEqualTo(fallback);
        assertThat(resolver.parseWindow(" ", fallback)).isEqualTo(fallback);
        assertThat(resolver.parseWindow("later", fallback)).isEqualTo(fallback);
    }

    @Test
    void parseWindowSupportsHourAndDaySuffixes() {
        assertThat(resolver.parseWindow("12h", Duration.ofDays(7))).isEqualTo(Duration.ofHours(12));
        assertThat(resolver.parseWindow("7d", Duration.ofHours(24))).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void resolveTrendGranularityKeepsValidInputAndDefaultsByWindowLength() {
        assertThat(resolver.resolveTrendGranularity("day", Duration.ofHours(6))).isEqualTo("day");
        assertThat(resolver.resolveTrendGranularity("hour", Duration.ofDays(7))).isEqualTo("hour");
        assertThat(resolver.resolveTrendGranularity(null, Duration.ofHours(48))).isEqualTo("hour");
        assertThat(resolver.resolveTrendGranularity("", Duration.ofHours(49))).isEqualTo("day");
    }
}
