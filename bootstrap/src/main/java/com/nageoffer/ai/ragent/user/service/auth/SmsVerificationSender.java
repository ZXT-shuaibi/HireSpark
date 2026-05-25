package com.nageoffer.ai.ragent.user.service.auth;

public interface SmsVerificationSender {

    void send(String phone, String code, SmsVerificationPurpose purpose);
}
