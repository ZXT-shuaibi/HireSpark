package com.nageoffer.ai.ragent.admin.service.dashboard;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class DashboardWindowResolver {

    public static final String GRANULARITY_DAY = "day";
    public static final String GRANULARITY_HOUR = "hour";

    public WindowRange resolveWindowRange(String window, Duration fallback) {
        Duration duration = parseWindow(window, fallback);
        Instant now = Instant.now();
        Instant start = now.minus(duration);
        Instant prevStart = start.minus(duration);
        String label = window == null ? formatDuration(fallback) : window;
        return new WindowRange(Date.from(start), Date.from(now), Date.from(prevStart), Date.from(start),
                label, "prev_" + label);
    }

    public Duration parseWindow(String window, Duration fallback) {
        if (window == null || window.isBlank()) {
            return fallback;
        }
        String normalized = window.trim().toLowerCase();
        if (normalized.endsWith("h")) {
            long hours = parseNumber(normalized.substring(0, normalized.length() - 1), fallback.toHours());
            return Duration.ofHours(hours);
        }
        if (normalized.endsWith("d")) {
            long days = parseNumber(normalized.substring(0, normalized.length() - 1), fallback.toDays());
            return Duration.ofDays(days);
        }
        return fallback;
    }

    public String resolveTrendGranularity(String granularity, Duration windowDuration) {
        if (granularity != null && !granularity.isBlank()) {
            String normalized = granularity.trim().toLowerCase();
            if (GRANULARITY_HOUR.equals(normalized) || GRANULARITY_DAY.equals(normalized)) {
                return normalized;
            }
        }
        return windowDuration.toHours() <= 48 ? GRANULARITY_HOUR : GRANULARITY_DAY;
    }

    public Date toDate(LocalDate date, ZoneId zoneId) {
        return Date.from(date.atStartOfDay(zoneId).toInstant());
    }

    public Date toDate(LocalDateTime time, ZoneId zoneId) {
        return Date.from(time.atZone(zoneId).toInstant());
    }

    public LocalDate toLocalDate(Date date, ZoneId zoneId) {
        return date.toInstant().atZone(zoneId).toLocalDate();
    }

    public LocalDateTime toLocalDateTime(Date date, ZoneId zoneId) {
        return date.toInstant().atZone(zoneId).toLocalDateTime();
    }

    private long parseNumber(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours % 24 == 0) {
            return (hours / 24) + "d";
        }
        return hours + "h";
    }

    public record WindowRange(Date start, Date end, Date prevStart, Date prevEnd,
                              String windowLabel, String compareLabel) {
    }
}
