package com.nageoffer.ai.ragent.conversation;

import com.nageoffer.ai.ragent.conversation.port.ConversationScene;
import com.nageoffer.ai.ragent.conversation.sequence.MessageSequenceScope;
import com.nageoffer.ai.ragent.conversation.sequence.RedisMessageSequenceAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSequenceAllocatorTest {

    @Test
    void allocatesMonotonicSequenceFromRedisByConversationScope() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ragent:conversation:sequence:RAG:u-1:c-1"))
                .thenReturn(1L)
                .thenReturn(2L);
        RedisMessageSequenceAllocator allocator = new RedisMessageSequenceAllocator(redisTemplate);

        MessageSequenceScope scope = new MessageSequenceScope(ConversationScene.RAG, "c-1", "u-1");

        assertThat(allocator.next(scope)).isEqualTo(1L);
        assertThat(allocator.next(scope)).isEqualTo(2L);
        verify(valueOperations, times(2)).increment("ragent:conversation:sequence:RAG:u-1:c-1");
    }

    @Test
    void fallsBackToLocalSequenceWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        RedisMessageSequenceAllocator allocator = new RedisMessageSequenceAllocator(redisTemplate);

        MessageSequenceScope scope = new MessageSequenceScope(ConversationScene.CAREER_INTERVIEW, "s-1", "u-1");

        assertThat(allocator.next(scope)).isEqualTo(1L);
        assertThat(allocator.next(scope)).isEqualTo(2L);
    }
}
