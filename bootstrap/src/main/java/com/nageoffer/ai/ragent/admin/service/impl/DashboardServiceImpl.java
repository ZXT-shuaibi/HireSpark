/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.admin.service.impl;

import com.nageoffer.ai.ragent.admin.controller.vo.DashboardOverviewGroupVO;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardOverviewKpiVO;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardOverviewVO;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardPerformanceVO;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardTrendSeriesVO;
import com.nageoffer.ai.ragent.admin.controller.vo.DashboardTrendsVO;
import com.nageoffer.ai.ragent.admin.service.DashboardService;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardBucketedStatsQueryService;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardMetricCalculator;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardRangeStatsQueryService;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardTimeBucket;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardTrendPointFactory;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardWindowResolver;
import com.nageoffer.ai.ragent.admin.service.dashboard.DashboardWindowResolver.WindowRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String NO_DOC_REPLY = "未检索到与问题相关的文档内容。";
    private static final long SLOW_LATENCY_THRESHOLD_MS = 20000L;
    private static final String SERIES_SESSIONS = "\u4f1a\u8bdd\u6570";
    private static final String SERIES_MESSAGES = "\u6d88\u606f\u6570";
    private static final String SERIES_ACTIVE_USERS = "\u6d3b\u8dc3\u7528\u6237";
    private static final String SERIES_AVG_LATENCY = "\u5e73\u5747\u54cd\u5e94\u65f6\u95f4";
    private static final String SERIES_ERROR_RATE = "\u9519\u8bef\u7387";
    private static final String SERIES_NO_DOC_RATE = "\u65e0\u77e5\u8bc6\u7387";

    private final DashboardRangeStatsQueryService rangeStatsQueryService;
    private final DashboardBucketedStatsQueryService bucketedStatsQueryService;
    private final DashboardTrendPointFactory trendPointFactory;
    private final DashboardWindowResolver windowResolver;
    private final DashboardMetricCalculator metricCalculator;

    @Override
    public DashboardOverviewVO loadOverview(String window) {
        WindowRange range = windowResolver.resolveWindowRange(window, Duration.ofHours(24));

        long totalUsers = rangeStatsQueryService.totalUsers();
        long usersInWindow = rangeStatsQueryService.countUsers(range.start(), range.end());

        long totalSessions = rangeStatsQueryService.totalConversations();
        long sessionsInWindow = rangeStatsQueryService.countConversations(range.start(), range.end());
        long sessionsPrevWindow = rangeStatsQueryService.countConversations(range.prevStart(), range.prevEnd());

        long totalMessages = rangeStatsQueryService.totalMessages();
        long messagesInWindow = rangeStatsQueryService.countMessages(range.start(), range.end());
        long messagesPrevWindow = rangeStatsQueryService.countMessages(range.prevStart(), range.prevEnd());

        long activeUsers = rangeStatsQueryService.countActiveUsers(range.start(), range.end());
        long activeUsersPrev = rangeStatsQueryService.countActiveUsers(range.prevStart(), range.prevEnd());

        DashboardOverviewGroupVO group = DashboardOverviewGroupVO.builder()
                .totalUsers(buildKpi(totalUsers, usersInWindow, null))
                .activeUsers(buildKpi(activeUsers, activeUsers - activeUsersPrev,
                        metricCalculator.calcPct(activeUsers, activeUsersPrev)))
                .totalSessions(buildKpi(totalSessions, sessionsInWindow, null))
                .sessions24h(buildKpi(sessionsInWindow, sessionsInWindow - sessionsPrevWindow,
                        metricCalculator.calcPct(sessionsInWindow, sessionsPrevWindow)))
                .totalMessages(buildKpi(totalMessages, messagesInWindow, null))
                .messages24h(buildKpi(messagesInWindow, messagesInWindow - messagesPrevWindow,
                        metricCalculator.calcPct(messagesInWindow, messagesPrevWindow)))
                .build();

        return DashboardOverviewVO.builder()
                .window(range.windowLabel())
                .compareWindow(range.compareLabel())
                .updatedAt(System.currentTimeMillis())
                .kpis(group)
                .build();
    }

    @Override
    public DashboardPerformanceVO loadPerformance(String window) {
        WindowRange range = windowResolver.resolveWindowRange(window, Duration.ofHours(24));
        List<Long> durations = rangeStatsQueryService.listSuccessfulDurations(range.start(), range.end(), STATUS_SUCCESS);
        long avgLatency = metricCalculator.average(durations);
        long p95Latency = metricCalculator.percentile95(durations);

        long success = rangeStatsQueryService.countTraceRuns(range.start(), range.end(), STATUS_SUCCESS);
        long error = rangeStatsQueryService.countTraceRuns(range.start(), range.end(), STATUS_ERROR);
        long total = success + error;
        long assistantCount = rangeStatsQueryService.countMessagesByRole(range.start(), range.end(), ROLE_ASSISTANT);
        long noDocCount = rangeStatsQueryService.countMessagesByRoleAndContent(
                range.start(), range.end(), ROLE_ASSISTANT, NO_DOC_REPLY);
        long slowCount = durations.stream().filter(duration -> duration > SLOW_LATENCY_THRESHOLD_MS).count();

        double successRate = rate(success, total);
        double errorRate = rate(error, total);
        double noDocRate = rate(noDocCount, assistantCount);
        double slowRate = rate(slowCount, durations.size());

        return DashboardPerformanceVO.builder()
                .window(range.windowLabel())
                .avgLatencyMs(avgLatency)
                .p95LatencyMs(p95Latency)
                .successRate(successRate)
                .errorRate(errorRate)
                .noDocRate(noDocRate)
                .slowRate(slowRate)
                .build();
    }

    @Override
    public DashboardTrendsVO loadTrends(String metric, String window, String granularity) {
        String normalizedMetric = metric == null ? "" : metric.trim().toLowerCase();
        Duration windowDuration = windowResolver.parseWindow(window, Duration.ofDays(7));
        WindowRange range = windowResolver.resolveWindowRange(window, Duration.ofDays(7));
        String resolvedGranularity = windowResolver.resolveTrendGranularity(granularity, windowDuration);
        ZoneId zoneId = ZoneId.systemDefault();
        List<DashboardTrendSeriesVO> series = new ArrayList<>();

        if (DashboardWindowResolver.GRANULARITY_HOUR.equals(resolvedGranularity)) {
            LocalDateTime endHourExclusive = windowResolver.toLocalDateTime(range.end(), zoneId)
                    .truncatedTo(ChronoUnit.HOURS)
                    .plusHours(1);
            LocalDateTime startHour = endHourExclusive.minusHours(Math.max(1, windowDuration.toHours()));
            DashboardTimeBucket<LocalDateTime> bucket = DashboardTimeBucket.hours(startHour, endHourExclusive, zoneId);
            appendTrendSeries(normalizedMetric, bucket, series);
        } else {
            LocalDate startDay = windowResolver.toLocalDate(range.start(), zoneId);
            LocalDate endExclusiveDay = windowResolver.toLocalDate(range.end(), zoneId).plusDays(1);
            DashboardTimeBucket<LocalDate> bucket = DashboardTimeBucket.days(startDay, endExclusiveDay, zoneId);
            appendTrendSeries(normalizedMetric, bucket, series);
        }

        return DashboardTrendsVO.builder()
                .metric(metric)
                .window(range.windowLabel())
                .granularity(resolvedGranularity)
                .series(series)
                .build();
    }

    private DashboardOverviewKpiVO buildKpi(long value, long delta, Double deltaPct) {
        return DashboardOverviewKpiVO.builder()
                .value(value)
                .delta(delta)
                .deltaPct(deltaPct)
                .build();
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return metricCalculator.round1((numerator * 100.0) / denominator);
    }

    private <T> void appendTrendSeries(
            String normalizedMetric,
            DashboardTimeBucket<T> bucket,
            List<DashboardTrendSeriesVO> series) {
        switch (normalizedMetric) {
            case "sessions" -> addLongSeries(
                    series, SERIES_SESSIONS, bucket, bucketedStatsQueryService.countConversations(bucket));
            case "messages" -> addLongSeries(
                    series, SERIES_MESSAGES, bucket, bucketedStatsQueryService.countMessages(bucket));
            case "activeusers" -> addLongSeries(
                    series, SERIES_ACTIVE_USERS, bucket, bucketedStatsQueryService.countActiveUsers(bucket));
            case "avglatency" -> addDoubleSeries(
                    series, SERIES_AVG_LATENCY, bucket, bucketedStatsQueryService.averageLatency(bucket, STATUS_SUCCESS));
            case "quality" -> appendQualitySeries(bucket, series);
            default -> {
            }
        }
    }

    private <T> void appendQualitySeries(DashboardTimeBucket<T> bucket, List<DashboardTrendSeriesVO> series) {
        Map<T, Long> successMap = bucketedStatsQueryService.countTraceRuns(bucket, STATUS_SUCCESS);
        Map<T, Long> errorMap = bucketedStatsQueryService.countTraceRuns(bucket, STATUS_ERROR);
        Map<T, Long> assistantCountMap = bucketedStatsQueryService.countMessagesByRole(bucket, ROLE_ASSISTANT);
        Map<T, Long> noDocCountMap = bucketedStatsQueryService.countMessagesByRoleAndContent(
                bucket, ROLE_ASSISTANT, NO_DOC_REPLY);
        Map<T, Double> errorRate = new HashMap<>();
        Map<T, Double> noDocRate = new HashMap<>();
        for (T key : bucket.keys()) {
            long error = errorMap.getOrDefault(key, 0L);
            long total = successMap.getOrDefault(key, 0L) + error;
            long assistantCount = assistantCountMap.getOrDefault(key, 0L);
            long noDocCount = noDocCountMap.getOrDefault(key, 0L);
            errorRate.put(key, rate(error, total));
            noDocRate.put(key, rate(noDocCount, assistantCount));
        }
        addDoubleSeries(series, SERIES_ERROR_RATE, bucket, errorRate);
        addDoubleSeries(series, SERIES_NO_DOC_RATE, bucket, noDocRate);
    }

    private <T> void addLongSeries(
            List<DashboardTrendSeriesVO> series,
            String name,
            DashboardTimeBucket<T> bucket,
            Map<T, Long> values) {
        series.add(DashboardTrendSeriesVO.builder()
                .name(name)
                .data(trendPointFactory.longPoints(bucket, values))
                .build());
    }

    private <T> void addDoubleSeries(
            List<DashboardTrendSeriesVO> series,
            String name,
            DashboardTimeBucket<T> bucket,
            Map<T, Double> values) {
        series.add(DashboardTrendSeriesVO.builder()
                .name(name)
                .data(trendPointFactory.doublePoints(bucket, values))
                .build());
    }

}
