/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.user.controller;

import com.nageoffer.ai.ragent.user.controller.request.LoginRequest;
import com.nageoffer.ai.ragent.user.controller.request.PasswordResetRequest;
import com.nageoffer.ai.ragent.user.controller.request.PhoneRegisterRequest;
import com.nageoffer.ai.ragent.user.controller.request.SmsCodeSendRequest;
import com.nageoffer.ai.ragent.user.controller.request.SmsCodeVerifyRequest;
import com.nageoffer.ai.ragent.user.controller.vo.LoginVO;
import com.nageoffer.ai.ragent.user.controller.vo.SmsTicketVO;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.user.service.AuthService;
import com.nageoffer.ai.ragent.user.service.auth.AuthPhoneFlowService;
import com.nageoffer.ai.ragent.user.service.auth.SmsVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 处理用户登录和登出相关的请求
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final SmsVerificationService smsVerificationService;

    private final AuthPhoneFlowService authPhoneFlowService;

    /**
     * 用户登录接口
     */
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@RequestBody LoginRequest requestParam) {
        return Results.success(authService.login(requestParam));
    }

    @PostMapping("/auth/sms/send")
    public Result<Void> sendSmsCode(@RequestBody SmsCodeSendRequest requestParam, HttpServletRequest request) {
        smsVerificationService.sendCode(requestParam.getPhone(), requestParam.getPurpose(), clientIp(request));
        return Results.success();
    }

    @PostMapping("/auth/sms/verify")
    public Result<SmsTicketVO> verifySmsCode(@RequestBody SmsCodeVerifyRequest requestParam) {
        String ticket = smsVerificationService.verifyCode(
                requestParam.getPhone(), requestParam.getPurpose(), requestParam.getCode());
        return Results.success(new SmsTicketVO(ticket));
    }

    @PostMapping("/auth/register")
    public Result<String> register(@RequestBody PhoneRegisterRequest requestParam) {
        return Results.success(authPhoneFlowService.register(requestParam));
    }

    @PostMapping("/auth/password/reset")
    public Result<Void> resetPassword(@RequestBody PasswordResetRequest requestParam) {
        authPhoneFlowService.resetPassword(requestParam);
        return Results.success();
    }

    /**
     * 用户登出接口，清除用户的认证信息和会话
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Results.success();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
