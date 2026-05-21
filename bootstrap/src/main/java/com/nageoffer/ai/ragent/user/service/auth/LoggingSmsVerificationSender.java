package com.nageoffer.ai.ragent.user.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ragent.auth.sms", name = "provider", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsVerificationSender implements SmsVerificationSender {

    @Override
    public void send(String phone, String code, SmsVerificationPurpose purpose) {
        log.info("SMS verification code issued, phone={}, purpose={}, code={}", phone, purpose.code(), code);
    }
}
