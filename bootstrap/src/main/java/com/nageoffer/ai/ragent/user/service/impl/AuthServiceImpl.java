package com.nageoffer.ai.ragent.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.controller.request.LoginRequest;
import com.nageoffer.ai.ragent.user.controller.vo.LoginVO;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.service.AuthService;
import com.nageoffer.ai.ragent.user.service.UserPasswordService;
import com.nageoffer.ai.ragent.user.service.auth.LoginFailureTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/583231?v=4";

    private final UserMapper userMapper;

    private final UserPasswordService passwordService;

    private final LoginFailureTracker loginFailureTracker;

    @Override
    public LoginVO login(LoginRequest requestParam) {
        String identifier = StrUtil.blankToDefault(requestParam.getUsername(), requestParam.getPhone());
        String password = requestParam.getPassword();
        if (StrUtil.isBlank(identifier) || StrUtil.isBlank(password)) {
            throw new ClientException("用户名/手机号或密码不能为空");
        }
        loginFailureTracker.ensureNotLocked(identifier);
        UserDO user = findByUsernameOrPhone(identifier);
        if (user == null || !passwordService.matches(password, user.getPassword())) {
            loginFailureTracker.recordFailure(identifier);
            throw new ClientException("用户名/手机号或密码错误");
        }
        if (user.getId() == null) {
            throw new ClientException("用户信息异常");
        }
        migrateLegacyPasswordIfNeeded(password, user);
        loginFailureTracker.clearFailures(identifier);
        loginFailureTracker.clearFailures(user.getUsername());
        loginFailureTracker.clearFailures(user.getPhone());
        String loginId = user.getId();
        StpUtil.login(loginId);
        String avatar = StrUtil.isBlank(user.getAvatar()) ? DEFAULT_AVATAR_URL : user.getAvatar();
        return new LoginVO(loginId, user.getRole(), StpUtil.getTokenValue(), avatar);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    private UserDO findByUsernameOrPhone(String identifier) {
        if (StrUtil.isBlank(identifier)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .and(wrapper -> wrapper.eq(UserDO::getUsername, identifier).or().eq(UserDO::getPhone, identifier))
                        .eq(UserDO::getDeleted, 0)
        );
    }

    private void migrateLegacyPasswordIfNeeded(String rawPassword, UserDO user) {
        if (passwordService.needsRehash(user.getPassword())) {
            user.setPassword(passwordService.encode(rawPassword));
            userMapper.updateById(user);
        }
    }
}
