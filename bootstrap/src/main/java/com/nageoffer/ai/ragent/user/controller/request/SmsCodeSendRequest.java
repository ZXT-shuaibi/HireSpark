package com.nageoffer.ai.ragent.user.controller.request;

import lombok.Data;

@Data
public class SmsCodeSendRequest {

    private String phone;

    private String purpose;
}
