package com.nageoffer.ai.ragent.user.service.auth;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

public enum SmsVerificationPurpose {

    REGISTER("register"),
    RESET_PASSWORD("reset_password");

    private final String code;

    SmsVerificationPurpose(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SmsVerificationPurpose parse(String value) {
        String normalized = StrUtil.trimToEmpty(value).toLowerCase();
        for (SmsVerificationPurpose purpose : values()) {
            if (purpose.code.equals(normalized)) {
                return purpose;
            }
        }
        throw new ClientException("验证码用途不支持");
    }
}
