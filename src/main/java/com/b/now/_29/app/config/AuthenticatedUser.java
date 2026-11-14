package com.b.now._29.app.config;

import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** 인증된 회원의 ID를 SecurityContext에 넣고 꺼낸다. principal이 회원 ID(Long)라는 사실은 이 클래스만 안다. */
final class AuthenticatedUser {

    private AuthenticatedUser() {}

    static void set(long userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    static Optional<Long> current() {
        return of(SecurityContextHolder.getContext().getAuthentication());
    }

    static Optional<Long> of(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long userId) {
            return Optional.of(userId);
        }
        return Optional.empty();
    }
}
