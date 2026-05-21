package com.nageoffer.ai.ragent.user.service;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.service.auth.LoginFailureTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginFailureTrackerTest {

    private final Map<String, String> redisValues = new HashMap<>();
    private LoginFailureTracker tracker;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> redisValues.containsKey(invocation.getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> redisValues.remove(invocation.getArgument(0)) != null);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisValues.getOrDefault(key, "0")) + 1;
            redisValues.put(key, String.valueOf(next));
            return next;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        tracker = new LoginFailureTracker(redisTemplate);
    }

    @Test
    void locksIdentifierAfterTenLoginFailures() {
        for (int i = 0; i < 10; i++) {
            tracker.recordFailure("13800000000");
        }

        assertThatThrownBy(() -> tracker.ensureNotLocked("13800000000"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("登录失败次数过多");
    }
}
