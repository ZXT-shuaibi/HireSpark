package com.nageoffer.ai.ragent.user.service;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.service.auth.SmsVerificationProperties;
import com.nageoffer.ai.ragent.user.service.auth.SmsVerificationPurpose;
import com.nageoffer.ai.ragent.user.service.auth.SmsVerificationSender;
import com.nageoffer.ai.ragent.user.service.auth.SmsVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmsVerificationServiceTest {

    private final Map<String, String> redisValues = new HashMap<>();
    private RecordingSmsVerificationSender sender;
    private SmsVerificationService smsVerificationService;

    @BeforeEach
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> redisValues.remove(invocation.getArgument(0)) != null);
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> redisValues.containsKey(invocation.getArgument(0)));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
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
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any(TimeUnit.class));

        sender = new RecordingSmsVerificationSender();
        SmsVerificationProperties properties = new SmsVerificationProperties();
        properties.setCodeTtl(Duration.ofMinutes(5));
        properties.setTicketTtl(Duration.ofMinutes(10));
        properties.setResendCooldown(Duration.ZERO);
        properties.setMaxSendPerWindow(3);
        properties.setSendWindow(Duration.ofMinutes(10));
        properties.setMaxVerifyFailures(3);
        smsVerificationService = new SmsVerificationService(redisTemplate, properties, sender, new UserPasswordService());
    }

    @Test
    void issuesTicketAfterCodeVerified() {
        smsVerificationService.sendCode("13800000000", "register", "127.0.0.1");

        String ticket = smsVerificationService.verifyCode("13800000000", "register", sender.lastCode);

        assertThat(ticket).isNotBlank();
        assertThat(smsVerificationService.consumeTicket("13800000000", "register", ticket)).isTrue();
        assertThat(smsVerificationService.consumeTicket("13800000000", "register", ticket)).isFalse();
    }

    @Test
    void storesHashedCodeAndInvalidatesAfterThreeWrongAttempts() {
        smsVerificationService.sendCode("13800000000", "register", "127.0.0.1");

        assertThat(redisValues.get(SmsVerificationService.codeValueKey("register", "13800000000")))
                .isNotEqualTo(sender.lastCode)
                .startsWith("pbkdf2$");
        assertThatThrownBy(() -> smsVerificationService.verifyCode("13800000000", "register", "000000"))
                .isInstanceOf(ClientException.class);
        assertThatThrownBy(() -> smsVerificationService.verifyCode("13800000000", "register", "111111"))
                .isInstanceOf(ClientException.class);
        assertThatThrownBy(() -> smsVerificationService.verifyCode("13800000000", "register", "222222"))
                .isInstanceOf(ClientException.class);

        assertThat(redisValues.get(SmsVerificationService.codeValueKey("register", "13800000000"))).isNull();
    }

    private static final class RecordingSmsVerificationSender implements SmsVerificationSender {

        private String lastCode;

        @Override
        public void send(String phone, String code, SmsVerificationPurpose purpose) {
            this.lastCode = code;
        }
    }
}
