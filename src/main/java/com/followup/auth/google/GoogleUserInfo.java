package com.followup.auth.google;

/** 검증에 성공한 구글 ID 토큰에서 뽑아낸 사용자 정보. */
public record GoogleUserInfo(
        String googleId,
        String email,
        boolean emailVerified,
        String name
) {
}
