package com.b.now._29.app.user;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {

    public static final String NAME = "access_token";

    private final boolean secure;

    public AuthCookies(@Value("${app.auth.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String token) {
        return build(token, TokenService.LIFETIME);
    }

    /** 발급할 때와 같은 속성으로 만료시켜야 브라우저가 지운다. */
    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}
