package com.nageoffer.ai.ragent.user.controller.request;

import lombok.Data;

@Data
public class PhoneRegisterRequest {

    private String phone;

    private String ticket;

    private String nickname;

    private String password;

    private String confirmPassword;
}
