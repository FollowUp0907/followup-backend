package com.followup.global.security;

import org.springframework.stereotype.Component;

// TEMPORARY: 인증 구현 전까지 항상 user id 1로 동작한다. 인증 추가 시 이 메서드만 교체하면 된다.
@Component
public class CurrentUserProvider {

    public Long getCurrentUserId() {
        return 1L;
    }
}
