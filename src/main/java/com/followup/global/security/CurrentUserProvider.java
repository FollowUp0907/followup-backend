package com.followup.global.security;

import org.springframework.stereotype.Component;

// TEMPORARY: hardcoded until JWT/Security is implemented. Not safe for real multi-user deployment —
// every request currently acts as user id 1. Replace getCurrentUserId() with a SecurityContext lookup
// when auth is added; nothing else in the codebase should need to change.
@Component
public class CurrentUserProvider {

    public Long getCurrentUserId() {
        return 1L;
    }
}
