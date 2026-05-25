package com.nageoffer.ai.ragent.user.service.auth;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginFailureTracker {

    private static final String KEY_PREFIX = "ragent:auth:login:";
    private static final int MAX_FAILURES = 10;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(30);
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    public void ensureNotLocked(String identifier) {
        String normalized = normalize(identifier);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(normalized)))) {
            throw new ClientException("登录失败次数过多，请稍后再试");
        }
    }

    public void recordFailure(String identifier) {
        String normalized = normalize(identifier);
        String failureKey = failureKey(normalized);
        Long count = stringRedisTemplate.opsForValue().increment(failureKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failureKey, FAILURE_WINDOW);
        }
        if (count != null && count >= MAX_FAILURES) {
            stringRedisTemplate.opsForValue().set(lockKey(normalized), "1", LOCK_TTL);
        }
    }

    public void clearFailures(String identifier) {
        String normalized = normalize(identifier);
        stringRedisTemplate.delete(failureKey(normalized));
        stringRedisTemplate.delete(lockKey(normalized));
    }

    private String normalize(String identifier) {
        return StrUtil.trimToEmpty(identifier).toLowerCase();
    }

    private String failureKey(String identifier) {
        return KEY_PREFIX + "fail:" + identifier;
    }

    private String lockKey(String identifier) {
        return KEY_PREFIX + "lock:" + identifier;
    }
}
