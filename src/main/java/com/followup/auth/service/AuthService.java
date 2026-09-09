package com.followup.auth.service;

import com.followup.auth.dto.LoginReqDto;
import com.followup.auth.dto.SignupReqDto;
import com.followup.auth.dto.SignupResDto;
import com.followup.auth.dto.TokenResDto;
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
}
