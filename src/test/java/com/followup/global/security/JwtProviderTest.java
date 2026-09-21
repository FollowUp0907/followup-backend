package com.followup.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!";
    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, ONE_DAY_MS);

    @Test
    void generateAccessToken_subjectIsUserId() {
        String token = jwtProvider.generateAccessToken(42L);

        Long userId = jwtProvider.validateAndGetUserId(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void validateAndGetUserId_malformedTokenRejected() {
        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void validateAndGetUserId_tamperedSignatureRejected() {
        String token = jwtProvider.generateAccessToken(1L);
        // 서명 세그먼트의 마지막 글자는 base64 패딩 비트라 바꿔도 디코딩된 바이트가 그대로일 때가 있다
        // (그래서 이 테스트가 가끔 flaky했다). 첫 글자는 항상 실제 서명 바이트의 상위 비트를 나타내므로
        // 어떤 문자로 바꾸든 반드시 디코딩 결과가 달라진다.
        String[] parts = token.split("\\.");
        char[] sigChars = parts[2].toCharArray();
        sigChars[0] = sigChars[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(sigChars);

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void validateAndGetUserId_expiredTokenRejected() {
        // Thread.sleep 없이 검증하기 위해 이미 지난 만료시간(음수)으로 발급한다.
        JwtProvider alreadyExpiredProvider = new JwtProvider(SECRET, -1_000L);
        String expiredToken = alreadyExpiredProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.validateAndGetUserId(expiredToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }
}
