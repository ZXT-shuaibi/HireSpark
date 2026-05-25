package com.nageoffer.ai.ragent.user.service.auth;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.service.UserPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    private static final String KEY_PREFIX = "ragent:auth:sms:";

    private final StringRedisTemplate stringRedisTemplate;

    private final SmsVerificationProperties properties;

    private final SmsVerificationSender sender;

    private final UserPasswordService passwordService;

    public void sendCode(String phone, String purposeValue, String clientIp) {
        String normalizedPhone = normalizePhone(phone);
        SmsVerificationPurpose purpose = SmsVerificationPurpose.parse(purposeValue);
        ensureSendAllowed(normalizedPhone, purpose, clientIp);
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(codeValueKey(purpose.code(), normalizedPhone), passwordService.encode(code),
                properties.getCodeTtl());
        stringRedisTemplate.delete(codeFailureKey(purpose.code(), normalizedPhone));
        sender.send(normalizedPhone, code, purpose);
    }

    public String verifyCode(String phone, String purposeValue, String code) {
        String normalizedPhone = normalizePhone(phone);
        SmsVerificationPurpose purpose = SmsVerificationPurpose.parse(purposeValue);
        String valueKey = codeValueKey(purpose.code(), normalizedPhone);
        String stored = stringRedisTemplate.opsForValue().get(valueKey);
        if (StrUtil.isBlank(stored)) {
            throw new ClientException("验证码已过期，请重新获取");
        }
        if (!passwordService.matches(StrUtil.trimToEmpty(code), stored)) {
            Long failures = stringRedisTemplate.opsForValue().increment(codeFailureKey(purpose.code(), normalizedPhone));
            if (failures != null && failures == 1L) {
                stringRedisTemplate.expire(codeFailureKey(purpose.code(), normalizedPhone), properties.getCodeTtl());
            }
            if (failures != null && failures >= properties.getMaxVerifyFailures()) {
                stringRedisTemplate.delete(valueKey);
                stringRedisTemplate.delete(codeFailureKey(purpose.code(), normalizedPhone));
                throw new ClientException("验证码错误次数过多，请重新获取");
            }
            throw new ClientException("验证码错误");
        }
        stringRedisTemplate.delete(valueKey);
        stringRedisTemplate.delete(codeFailureKey(purpose.code(), normalizedPhone));
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(ticketKey(purpose.code(), normalizedPhone), passwordService.encode(ticket),
                properties.getTicketTtl());
        return ticket;
    }

    public boolean consumeTicket(String phone, String purposeValue, String ticket) {
        String normalizedPhone = normalizePhone(phone);
        SmsVerificationPurpose purpose = SmsVerificationPurpose.parse(purposeValue);
        String key = ticketKey(purpose.code(), normalizedPhone);
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(stored) || !passwordService.matches(StrUtil.trimToEmpty(ticket), stored)) {
            return false;
        }
        stringRedisTemplate.delete(key);
        return true;
    }

    public static String codeValueKey(String purpose, String phone) {
        return KEY_PREFIX + "code:" + purpose + ":" + phone;
    }

    private void ensureSendAllowed(String phone, SmsVerificationPurpose purpose, String clientIp) {
        String cooldownKey = KEY_PREFIX + "cooldown:" + purpose.code() + ":" + phone;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            throw new ClientException("验证码发送过于频繁，请稍后再试");
        }
        incrementWithWindow(KEY_PREFIX + "send:" + purpose.code() + ":" + phone,
                properties.getMaxSendPerWindow(), properties.getSendWindow(), "验证码发送次数过多，请稍后再试");
        if (StrUtil.isNotBlank(clientIp)) {
            incrementWithWindow(KEY_PREFIX + "ip-send:" + clientIp,
                    properties.getMaxIpSendPerWindow(), properties.getIpSendWindow(), "验证码发送请求过多，请稍后再试");
        }
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", properties.getResendCooldown().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void incrementWithWindow(String key, int maxCount, Duration window, String message) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, window);
        }
        if (count != null && count > maxCount) {
            throw new ClientException(message);
        }
    }

    private String normalizePhone(String phone) {
        String normalized = StrUtil.trimToEmpty(phone);
        if (!normalized.matches("^1\\d{10}$")) {
            throw new ClientException("手机号格式不正确");
        }
        return normalized;
    }

    private static String codeFailureKey(String purpose, String phone) {
        return KEY_PREFIX + "code-failure:" + purpose + ":" + phone;
    }

    private static String ticketKey(String purpose, String phone) {
        return KEY_PREFIX + "ticket:" + purpose + ":" + phone;
    }
}
