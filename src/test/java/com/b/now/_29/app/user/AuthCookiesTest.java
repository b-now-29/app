package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.b.now._29.app.support.SetCookieHeader;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 비밀번호 변경 성공 응답의 쿠키 덮어쓰기 (docs/features/password-change.md R5, D8). 연장 필터가 먼저 붙인 옛 버전 쿠키가 남지 않고 새 버전 쿠키
 * 하나만 남아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthCookiesTest {

    private static final TokenSubject NEW_SUBJECT = new TokenSubject(1L, 1);
    private static final IssuedToken NEW_TOKEN = new IssuedToken("new-version-token", Duration.ofHours(1));

    @Mock
    TokenService tokenService;

    @Test
    @DisplayName("이미 붙은 연장 쿠키가 있어도 overwriteFor 뒤의 Set-Cookie는 새 토큰 쿠키 하나뿐이다 (R5, D8)")
    void overwriteReplacesRenewedCookie() {
        given(tokenService.issue(NEW_SUBJECT)).willReturn(NEW_TOKEN);
        AuthCookies authCookies = new AuthCookies(true, tokenService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.addHeader(
                HttpHeaders.SET_COOKIE, authCookies.issue(new IssuedToken("renewed-old-version", Duration.ofHours(1))));

        authCookies.overwriteFor(response, NEW_SUBJECT);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).containsExactly(authCookies.issue(NEW_TOKEN));
    }

    @Test
    @DisplayName("붙은 쿠키가 없으면 overwriteFor는 issueFor와 같은 속성의 새 토큰 쿠키 하나를 붙인다 (R5)")
    void overwriteWithoutExistingCookie() {
        given(tokenService.issue(NEW_SUBJECT)).willReturn(NEW_TOKEN);
        AuthCookies authCookies = new AuthCookies(true, tokenService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookies.overwriteFor(response, NEW_SUBJECT);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        SetCookieHeader cookie = SetCookieHeader.parse(response.getHeader(HttpHeaders.SET_COOKIE));
        assertThat(cookie.name()).isEqualTo("access_token");
        assertThat(cookie.value()).isEqualTo("new-version-token");
        assertThat(cookie.attributes()).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=3600");
    }
}
