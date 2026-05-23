package com.nageoffer.ai.ragent.admin.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardRangeStatsQueryService {

    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final RagTraceRunMapper traceRunMapper;

    public long totalUsers() {
        return userMapper.selectCount(Wrappers.lambdaQuery(UserDO.class));
    }

    public long totalConversations() {
        return conversationMapper.selectCount(Wrappers.lambdaQuery(ConversationDO.class));
    }

    public long totalMessages() {
        return messageMapper.selectCount(Wrappers.lambdaQuery(ConversationMessageDO.class));
    }

    public long countUsers(Date start, Date end) {
        return userMapper.selectCount(Wrappers.lambdaQuery(UserDO.class)
                .ge(UserDO::getCreateTime, start)
                .lt(UserDO::getCreateTime, end));
    }

    public long countConversations(Date start, Date end) {
        return conversationMapper.selectCount(Wrappers.lambdaQuery(ConversationDO.class)
                .ge(ConversationDO::getCreateTime, start)
                .lt(ConversationDO::getCreateTime, end));
    }

    public long countMessages(Date start, Date end) {
        return messageMapper.selectCount(messageRange(start, end));
    }

    public long countMessagesByRole(Date start, Date end, String role) {
        return messageMapper.selectCount(messageRange(start, end).eq("role", role));
    }

    public long countMessagesByRoleAndContent(Date start, Date end, String role, String content) {
        return messageMapper.selectCount(messageRange(start, end)
                .eq("role", role)
                .eq("content", content));
    }

    public long countActiveUsers(Date start, Date end) {
        QueryWrapper<ConversationMessageDO> wrapper = new QueryWrapper<>();
        wrapper.select("count(distinct user_id) as cnt")
                .ge("create_time", start)
                .lt("create_time", end);
        return extractCount(messageMapper.selectMaps(wrapper));
    }

    public long countTraceRuns(Date start, Date end, String status) {
        QueryWrapper<RagTraceRunDO> wrapper = new QueryWrapper<>();
        wrapper.ge("start_time", start).lt("start_time", end);
        if (status != null) {
            wrapper.eq("status", status);
        }
        return traceRunMapper.selectCount(wrapper);
    }

    public List<Long> listSuccessfulDurations(Date start, Date end, String successStatus) {
        QueryWrapper<RagTraceRunDO> wrapper = new QueryWrapper<>();
        wrapper.select("duration_ms")
                .ge("start_time", start)
                .lt("start_time", end)
                .eq("status", successStatus);
        List<Object> results = traceRunMapper.selectObjs(wrapper);
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> durations = new ArrayList<>();
        for (Object value : results) {
            if (value instanceof Number number) {
                long duration = number.longValue();
                if (duration > 0) {
                    durations.add(duration);
                }
            }
        }
        return durations;
    }

    private QueryWrapper<ConversationMessageDO> messageRange(Date start, Date end) {
        QueryWrapper<ConversationMessageDO> wrapper = new QueryWrapper<>();
        wrapper.ge("create_time", start).lt("create_time", end);
        return wrapper;
    }

    private long extractCount(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return 0L;
        }
        Object value = maps.get(0).get("cnt");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
