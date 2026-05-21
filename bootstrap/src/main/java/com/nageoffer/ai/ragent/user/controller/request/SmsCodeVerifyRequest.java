package com.nageoffer.ai.ragent.user.controller.request;

import lombok.Data;

@Data
public class SmsCodeVerifyRequest {

    private String phone;

    private String purpose;

    private String code;
}
