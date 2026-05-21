package com.nageoffer.ai.ragent.user.service.auth;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.controller.request.PasswordResetRequest;
import com.nageoffer.ai.ragent.user.controller.request.PhoneRegisterRequest;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.enums.UserRole;
import com.nageoffer.ai.ragent.user.service.UserPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthPhoneFlowService {

    private final UserMapper userMapper;

    private final SmsVerificationService smsVerificationService;

    private final UserPasswordService passwordService;

    private final LoginFailureTracker loginFailureTracker;

    public String register(PhoneRegisterRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        String phone = normalizePhone(request.getPhone());
        String nickname = StrUtil.trimToNull(request.getNickname());
        String password = StrUtil.trimToNull(request.getPassword());
        String confirmPassword = StrUtil.trimToNull(request.getConfirmPassword());
        Assert.notBlank(nickname, () -> new ClientException("昵称不能为空"));
        assertPasswordsMatch(password, confirmPassword);
        if (!smsVerificationService.consumeTicket(phone, SmsVerificationPurpose.REGISTER.code(), request.getTicket())) {
            throw new ClientException("验证码校验已失效，请重新验证");
        }
        ensurePhoneAvailable(phone);
        ensureUsernameAvailable(nickname);
        UserDO record = UserDO.builder()
                .phone(phone)
                .username(nickname)
                .password(passwordService.encode(password))
                .role(UserRole.USER.getCode())
                .build();
        userMapper.insert(record);
        loginFailureTracker.clearFailures(phone);
        return String.valueOf(record.getId());
    }

    public void resetPassword(PasswordResetRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        String phone = normalizePhone(request.getPhone());
        String password = StrUtil.trimToNull(request.getPassword());
        String confirmPassword = StrUtil.trimToNull(request.getConfirmPassword());
        assertPasswordsMatch(password, confirmPassword);
        if (!smsVerificationService.consumeTicket(phone, SmsVerificationPurpose.RESET_PASSWORD.code(), request.getTicket())) {
            throw new ClientException("验证码校验已失效，请重新验证");
        }
        UserDO record = findByPhone(phone);
        Assert.notNull(record, () -> new ClientException("手机号未注册"));
        record.setPassword(passwordService.encode(password));
        userMapper.updateById(record);
        loginFailureTracker.clearFailures(phone);
        loginFailureTracker.clearFailures(record.getUsername());
    }

    private void assertPasswordsMatch(String password, String confirmPassword) {
        Assert.notBlank(password, () -> new ClientException("密码不能为空"));
        Assert.notBlank(confirmPassword, () -> new ClientException("确认密码不能为空"));
        if (!password.equals(confirmPassword)) {
            throw new ClientException("两次密码输入不一致");
        }
    }

    private void ensurePhoneAvailable(String phone) {
        if (findByPhone(phone) != null) {
            throw new ClientException("手机号已注册");
        }
    }

    private void ensureUsernameAvailable(String username) {
        UserDO existing = userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getDeleted, 0)
        );
        if (existing != null) {
            throw new ClientException("昵称已被使用");
        }
    }

    private UserDO findByPhone(String phone) {
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getPhone, phone)
                        .eq(UserDO::getDeleted, 0)
        );
    }

    private String normalizePhone(String phone) {
        String normalized = StrUtil.trimToEmpty(phone);
        if (!normalized.matches("^1\\d{10}$")) {
            throw new ClientException("手机号格式不正确");
        }
        return normalized;
    }
}
