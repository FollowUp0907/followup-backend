package com.followup.global.security;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** SecurityContext에 저장된 인증 사용자의 userId를 반환한다. */
@Component
public class CurrentUserProvider {

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
