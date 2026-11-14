package com.b.now._29.app.user;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {

    private static final String NAME = "access_token";

    private final boolean secure;
    private final TokenService tokenService;

    public AuthCookies(@Value("${app.auth.cookie-secure}") boolean secure, TokenService tokenService) {
        this.secure = secure;
        this.tokenService = tokenService;
    }

    /** 요청 쿠키에서 토큰을 꺼낸다. */
    public Optional<String> tokenFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** 회원의 새 토큰을 담은 {@code Set-Cookie} 헤더 값. */
    public String issueFor(TokenSubject subject) {
        return issue(tokenService.issue(subject));
    }

    public String issue(IssuedToken token) {
        return build(token.value(), token.maxAge());
    }

    /** 발급할 때와 같은 속성으로 만료시켜야 브라우저가 지운다. */
    public String expire() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
