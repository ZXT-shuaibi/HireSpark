package com.nageoffer.ai.ragent.admin.service.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

public record DashboardTimeBucket<T>(String alias,
                                     String selectExpression,
                                     Date startDate,
                                     Date endDate,
                                     List<T> keys,
                                     Function<Object, T> parser,
                                     Function<T, Long> timestampExtractor) {

    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static DashboardTimeBucket<LocalDate> days(LocalDate start, LocalDate endExclusive, ZoneId zoneId) {
        List<LocalDate> keys = new ArrayList<>();
        for (LocalDate cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusDays(1)) {
            keys.add(cursor);
        }
        return new DashboardTimeBucket<>(
                "d",
                "to_char(%s,'YYYY-MM-DD') as d",
                toDate(start, zoneId),
                toDate(endExclusive, zoneId),
                keys,
                DashboardTimeBucket::parseLocalDate,
                day -> toDate(day, zoneId).getTime());
    }

    public static DashboardTimeBucket<LocalDateTime> hours(
            LocalDateTime start,
            LocalDateTime endExclusive,
            ZoneId zoneId) {
        List<LocalDateTime> keys = new ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusHours(1)) {
            keys.add(cursor);
        }
        return new DashboardTimeBucket<>(
                "h",
                "to_char(%s,'YYYY-MM-DD HH24:00:00') as h",
                toDate(start, zoneId),
                toDate(endExclusive, zoneId),
                keys,
                DashboardTimeBucket::parseLocalDateTime,
                hour -> toDate(hour, zoneId).getTime());
    }

    public String selectExpression(String columnName) {
        return selectExpression.formatted(columnName);
    }

    private static LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value), HOUR_FORMATTER);
    }

    private static Date toDate(LocalDate date, ZoneId zoneId) {
        return Date.from(date.atStartOfDay(zoneId).toInstant());
    }

    private static Date toDate(LocalDateTime time, ZoneId zoneId) {
        return Date.from(time.atZone(zoneId).toInstant());
    }
}
