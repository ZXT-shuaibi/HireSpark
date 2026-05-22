package com.nageoffer.ai.ragent.core.idempotency;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreventDuplicateSubmitAspectTest {

    @Test
    void proceedsWhenRedisLockIsAcquired() throws Throwable {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("1"), eq(Duration.ofSeconds(3)))).thenReturn(true);
        PreventDuplicateSubmitAspect aspect = new PreventDuplicateSubmitAspect(redisTemplate);
        ProceedingJoinPoint joinPoint = joinPoint("answer", "u-1");

        Object result = aspect.around(joinPoint, annotation());

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void rejectsDuplicateSubmitWhenRedisLockExists() throws Throwable {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("1"), eq(Duration.ofSeconds(3)))).thenReturn(false);
        PreventDuplicateSubmitAspect aspect = new PreventDuplicateSubmitAspect(redisTemplate);
        ProceedingJoinPoint joinPoint = joinPoint("answer", "u-1");

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Duplicate submit");
        verify(joinPoint, never()).proceed();
    }

    private ProceedingJoinPoint joinPoint(Object... args) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toLongString()).thenReturn("public void submit(java.lang.String,java.lang.String)");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn("ok");
        return joinPoint;
    }

    private PreventDuplicateSubmit annotation() throws NoSuchMethodException {
        Method method = AnnotatedSamples.class.getDeclaredMethod("submit");
        return method.getAnnotation(PreventDuplicateSubmit.class);
    }

    private static class AnnotatedSamples {
        @PreventDuplicateSubmit(ttlSeconds = 3)
        void submit() {
        }
    }
}
