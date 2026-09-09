package com.followup.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.followup.auth.dto.LoginReqDto;
import com.followup.auth.dto.SignupReqDto;
import com.followup.auth.dto.SignupResDto;
import com.followup.auth.dto.TokenResDto;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    private SignupReqDto signupRequest(String email) {
        return new SignupReqDto(email, "password123", "Tester");
    }

    @Test
    void signup_success() {
        String email = uniqueEmail();

        SignupResDto response = authService.signup(signupRequest(email));

        assertThat(response.email()).isEqualTo(email);
        assertThat(response.name()).isEqualTo("Tester");
        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void signup_duplicateEmailRejected() {
        String email = uniqueEmail();
        authService.signup(signupRequest(email));

        assertThatThrownBy(() -> authService.signup(signupRequest(email)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void signup_passwordIsBcryptEncoded() {
        String email = uniqueEmail();

        authService.signup(signupRequest(email));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void login_success() {
        String email = uniqueEmail();
        authService.signup(signupRequest(email));

        TokenResDto token = authService.login(new LoginReqDto(email, "password123"));

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isPositive();
    }

    @Test
    void login_unknownEmailRejected() {
        assertThatThrownBy(() -> authService.login(new LoginReqDto(uniqueEmail(), "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_wrongPasswordRejected() {
        String email = uniqueEmail();
        authService.signup(signupRequest(email));

        assertThatThrownBy(() -> authService.login(new LoginReqDto(email, "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}
