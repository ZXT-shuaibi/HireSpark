package com.nageoffer.ai.ragent.conversation.sequence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisMessageSequenceAllocator implements MessageSequenceAllocator {

    private static final String KEY_PREFIX = "ragent:conversation:sequence:";

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentMap<String, AtomicLong> localSequences = new ConcurrentHashMap<>();

    public RedisMessageSequenceAllocator(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable());
    }

    public RedisMessageSequenceAllocator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long next(MessageSequenceScope scope) {
        String key = key(scope);
        if (redisTemplate != null) {
            try {
                Long value = redisTemplate.opsForValue().increment(key);
                if (value != null && value > 0) {
                    return value;
                }
            } catch (RuntimeException ignored) {
                // Redis is an optimization here; local monotonic fallback keeps the flow available.
            }
        }
        return localSequences.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private String key(MessageSequenceScope scope) {
        return KEY_PREFIX + scope.scene().name() + ":" + scope.userId() + ":" + scope.conversationId();
    }
}
