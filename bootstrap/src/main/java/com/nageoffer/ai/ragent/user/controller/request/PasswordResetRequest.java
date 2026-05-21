package com.nageoffer.ai.ragent.user.controller.request;

import lombok.Data;

@Data
public class PasswordResetRequest {

    private String phone;

    private String ticket;

    private String password;

    private String confirmPassword;
}
