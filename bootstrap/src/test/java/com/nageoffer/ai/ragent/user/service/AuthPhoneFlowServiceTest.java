package com.nageoffer.ai.ragent.user.service;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.controller.request.PhoneRegisterRequest;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.service.auth.AuthPhoneFlowService;
import com.nageoffer.ai.ragent.user.service.auth.LoginFailureTracker;
import com.nageoffer.ai.ragent.user.service.auth.SmsVerificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthPhoneFlowServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final SmsVerificationService smsVerificationService = mock(SmsVerificationService.class);
    private final UserPasswordService passwordService = new UserPasswordService();
    private final LoginFailureTracker loginFailureTracker = mock(LoginFailureTracker.class);
    private final AuthPhoneFlowService service = new AuthPhoneFlowService(
            userMapper, smsVerificationService, passwordService, loginFailureTracker);

    @Test
    void registerRequiresMatchingPasswordsAndVerifiedTicket() {
        PhoneRegisterRequest request = new PhoneRegisterRequest();
        request.setPhone("13800000000");
        request.setTicket("ticket");
        request.setNickname("tester");
        request.setPassword("S3cret-pass");
        request.setConfirmPassword("S3cret-pass");
        when(smsVerificationService.consumeTicket("13800000000", "register", "ticket")).thenReturn(true);

        service.register(request);

        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPhone()).isEqualTo("13800000000");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUsername()).isEqualTo("tester");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPassword()).isNotEqualTo("S3cret-pass");
        org.assertj.core.api.Assertions.assertThat(passwordService.matches("S3cret-pass", captor.getValue().getPassword()))
                .isTrue();
    }

    @Test
    void rejectsRegisterWhenPasswordConfirmationDiffers() {
        PhoneRegisterRequest request = new PhoneRegisterRequest();
        request.setPhone("13800000000");
        request.setTicket("ticket");
        request.setNickname("tester");
        request.setPassword("S3cret-pass");
        request.setConfirmPassword("other-pass");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("两次密码");
    }

    @Test
    void rejectsRegisterWithoutVerifiedTicket() {
        PhoneRegisterRequest request = new PhoneRegisterRequest();
        request.setPhone("13800000000");
        request.setTicket("ticket");
        request.setNickname("tester");
        request.setPassword("S3cret-pass");
        request.setConfirmPassword("S3cret-pass");
        when(smsVerificationService.consumeTicket("13800000000", "register", "ticket")).thenReturn(false);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("验证码");
    }
}
