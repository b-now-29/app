package com.b.now._29.app.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그아웃 응답의 쿠키를 확인한다. 연장 쿠키가 함께 내려가면 브라우저가 어느 쪽을 남길지 정해지지 않으므로, {@code Set-Cookie}는 인증 쿠키를 지우는 것 하나뿐이어야
 * 한다.
 */
public final class ExpiredAuthCookie {

    private static final String COOKIE_NAME = "access_token";

    private ExpiredAuthCookie() {}

    public static void assertOnlyExpiredCookie(MvcResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        SetCookieHeader cookie = SetCookieHeader.from(result);
        assertThat(cookie.name()).isEqualTo(COOKIE_NAME);
        assertThat(cookie.value()).isEmpty();
        assertThat(cookie.attributes()).contains("Max-Age=0", "Path=/api");
    }
}
