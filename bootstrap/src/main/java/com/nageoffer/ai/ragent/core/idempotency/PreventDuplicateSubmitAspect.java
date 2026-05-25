package com.nageoffer.ai.ragent.core.idempotency;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;

@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class PreventDuplicateSubmitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(preventDuplicateSubmit)")
    public Object around(ProceedingJoinPoint joinPoint,
                         PreventDuplicateSubmit preventDuplicateSubmit) throws Throwable {
        String key = buildKey(joinPoint, preventDuplicateSubmit);
        Duration ttl = Duration.ofSeconds(Math.max(1L, preventDuplicateSubmit.ttlSeconds()));
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ClientException("Duplicate submit is not allowed");
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (preventDuplicateSubmit.releaseOnCompletion()) {
                stringRedisTemplate.delete(key);
            }
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, PreventDuplicateSubmit annotation) {
        String signature = joinPoint.getSignature() == null ? "unknown" : joinPoint.getSignature().toLongString();
        String args = Arrays.deepToString(joinPoint.getArgs());
        return annotation.keyPrefix() + ":" + sha256(signature + ":" + args);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ClientException("Failed to build duplicate submit key");
        }
    }
}
