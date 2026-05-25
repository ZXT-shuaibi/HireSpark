package com.nageoffer.ai.ragent.admin.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardBucketedStatsQueryService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final RagTraceRunMapper traceRunMapper;
    private final DashboardMetricCalculator metricCalculator;

    public <T> Map<T, Long> countConversations(DashboardTimeBucket<T> bucket) {
        QueryWrapper<ConversationDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "create_time", "count(*) as cnt");
        return mapLongResults(bucket, conversationMapper.selectMaps(wrapper));
    }

    public <T> Map<T, Long> countMessages(DashboardTimeBucket<T> bucket) {
        QueryWrapper<ConversationMessageDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "create_time", "count(*) as cnt");
        return mapLongResults(bucket, messageMapper.selectMaps(wrapper));
    }

    public <T> Map<T, Long> countMessagesByRole(DashboardTimeBucket<T> bucket, String role) {
        QueryWrapper<ConversationMessageDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "create_time", "count(*) as cnt");
        wrapper.eq("role", role);
        return mapLongResults(bucket, messageMapper.selectMaps(wrapper));
    }

    public <T> Map<T, Long> countMessagesByRoleAndContent(DashboardTimeBucket<T> bucket, String role, String content) {
        QueryWrapper<ConversationMessageDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "create_time", "count(*) as cnt");
        wrapper.eq("role", role).eq("content", content);
        return mapLongResults(bucket, messageMapper.selectMaps(wrapper));
    }

    public <T> Map<T, Long> countActiveUsers(DashboardTimeBucket<T> bucket) {
        QueryWrapper<ConversationMessageDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "create_time", "count(distinct user_id) as cnt");
        return mapLongResults(bucket, messageMapper.selectMaps(wrapper));
    }

    public <T> Map<T, Double> averageLatency(DashboardTimeBucket<T> bucket, String successStatus) {
        QueryWrapper<RagTraceRunDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "start_time", "avg(duration_ms) as avg");
        wrapper.eq("status", successStatus);
        return mapDoubleResults(bucket, traceRunMapper.selectMaps(wrapper));
    }

    public <T> Map<T, Long> countTraceRuns(DashboardTimeBucket<T> bucket, String status) {
        QueryWrapper<RagTraceRunDO> wrapper = new QueryWrapper<>();
        applyBucket(wrapper, bucket, "start_time", "count(*) as cnt");
        if (status != null) {
            wrapper.eq("status", status);
        }
        return mapLongResults(bucket, traceRunMapper.selectMaps(wrapper));
    }

    private void applyBucket(QueryWrapper<?> wrapper, DashboardTimeBucket<?> bucket, String timeColumn, String aggregate) {
        wrapper.select(bucket.selectExpression(timeColumn), aggregate)
                .ge(timeColumn, bucket.startDate())
                .lt(timeColumn, bucket.endDate())
                .groupBy(bucket.alias());
    }

    private <T> Map<T, Long> mapLongResults(DashboardTimeBucket<T> bucket, List<Map<String, Object>> maps) {
        Map<T, Long> result = new HashMap<>();
        if (maps == null) {
            return result;
        }
        for (Map<String, Object> row : maps) {
            T key = bucket.parser().apply(row.get(bucket.alias()));
            Long value = toLongValue(row.get("cnt"));
            if (key != null && value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private <T> Map<T, Double> mapDoubleResults(DashboardTimeBucket<T> bucket, List<Map<String, Object>> maps) {
        Map<T, Double> result = new HashMap<>();
        if (maps == null) {
            return result;
        }
        for (Map<String, Object> row : maps) {
            T key = bucket.parser().apply(row.get(bucket.alias()));
            Object value = row.get("avg");
            double avg = value instanceof Number number ? number.doubleValue() : 0.0;
            if (key != null) {
                result.put(key, metricCalculator.round1(avg));
            }
        }
        return result;
    }

    private Long toLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
