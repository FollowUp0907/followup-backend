package com.followup.auth.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final GoogleIdTokenVerifierComponent googleIdTokenVerifierComponent;

    @Transactional
    public SignupResDto signup(SignupReqDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();
        userRepository.save(user);

        return SignupResDto.from(user);
    }

    /** 존재하지 않는 이메일과 비밀번호 불일치를 동일하게 처리해 계정 존재 여부가 노출되지 않도록 한다. */
    @Transactional(readOnly = true)
    public TokenResDto login(LoginReqDto request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        return new TokenResDto(accessToken, "Bearer", jwtProvider.getExpirationMs());
    }

    /**
     * googleId로 먼저 조회하고, 없으면 email로 기존(비밀번호) 계정을 찾아 구글 계정을 연결한다 —
     * 이메일이 같은데 계정이 중복 생성되는 것을 막기 위함이다. 그마저도 없으면 새로 만든다.
     */
    @Transactional
    public TokenResDto loginWithGoogle(String idToken) {
        GoogleUserInfo googleUser = googleIdTokenVerifierComponent.verify(idToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN));

        if (!googleUser.emailVerified()) {
            throw new BusinessException(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
        }

        User user = userRepository.findByGoogleId(googleUser.googleId())
                .orElseGet(() -> userRepository.findByEmail(googleUser.email())
                        .map(existing -> {
                            existing.linkGoogleAccount(googleUser.googleId());
                            return existing;
                        })
                        .orElseGet(() -> userRepository.save(User.builder()
                                .email(googleUser.email())
                                .password(null)
                                .name(googleUser.name())
                                .googleId(googleUser.googleId())
                                .build())));

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        return new TokenResDto(accessToken, "Bearer", jwtProvider.getExpirationMs());
    }
}
