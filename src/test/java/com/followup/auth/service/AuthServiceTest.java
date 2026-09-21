package com.followup.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.followup.auth.dto.LoginReqDto;
import com.followup.auth.dto.SignupReqDto;
import com.followup.auth.dto.SignupResDto;
import com.followup.auth.dto.TokenResDto;
import com.followup.auth.google.GoogleIdTokenVerifierComponent;
import com.followup.auth.google.GoogleUserInfo;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.JwtProvider;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private GoogleIdTokenVerifierComponent googleIdTokenVerifierComponent;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    private String uniqueGoogleId() {
        return "google-" + UUID.randomUUID();
    }

    private void stubGoogleVerify(String idToken, String googleId, String email, boolean emailVerified, String name) {
        when(googleIdTokenVerifierComponent.verify(idToken))
                .thenReturn(Optional.of(new GoogleUserInfo(googleId, email, emailVerified, name)));
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

    @Test
    void loginWithGoogle_newAccount_createsUserAndLogsIn() {
        String email = uniqueEmail();
        String googleId = uniqueGoogleId();
        stubGoogleVerify("valid-token", googleId, email, true, "Google User");

        TokenResDto token = authService.loginWithGoogle("valid-token");

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.tokenType()).isEqualTo("Bearer");
        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getGoogleId()).isEqualTo(googleId);
        assertThat(saved.getName()).isEqualTo("Google User");
        assertThat(saved.getPassword()).isNull();
        assertThat(jwtProvider.validateAndGetUserId(token.accessToken())).isEqualTo(saved.getId());
    }

    @Test
    void loginWithGoogle_existingGoogleAccount_logsInSameUser() {
        String email = uniqueEmail();
        String googleId = uniqueGoogleId();
        stubGoogleVerify("valid-token", googleId, email, true, "Google User");
        authService.loginWithGoogle("valid-token");
        Long firstUserId = userRepository.findByEmail(email).orElseThrow().getId();

        TokenResDto token = authService.loginWithGoogle("valid-token");

        assertThat(jwtProvider.validateAndGetUserId(token.accessToken())).isEqualTo(firstUserId);
        assertThat(userRepository.findByGoogleId(googleId)).isPresent();
    }

    @Test
    void loginWithGoogle_existingPasswordAccount_linksGoogleIdWithoutDuplicating() {
        String email = uniqueEmail();
        authService.signup(signupRequest(email));
        Long existingUserId = userRepository.findByEmail(email).orElseThrow().getId();
        String googleId = uniqueGoogleId();
        stubGoogleVerify("valid-token", googleId, email, true, "Google User");

        TokenResDto token = authService.loginWithGoogle("valid-token");

        User linked = userRepository.findByEmail(email).orElseThrow();
        assertThat(linked.getId()).isEqualTo(existingUserId);
        assertThat(linked.getGoogleId()).isEqualTo(googleId);
        assertThat(linked.getPassword()).isNotNull();
        assertThat(jwtProvider.validateAndGetUserId(token.accessToken())).isEqualTo(existingUserId);
    }

    @Test
    void loginWithGoogle_invalidToken_rejected() {
        when(googleIdTokenVerifierComponent.verify(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithGoogle("bad-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void loginWithGoogle_emailNotVerified_rejected() {
        stubGoogleVerify("valid-token", uniqueGoogleId(), uniqueEmail(), false, "Google User");

        assertThatThrownBy(() -> authService.loginWithGoogle("valid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
    }
}
