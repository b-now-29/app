package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.b.now._29.app.user.AuthCookies;
import com.b.now._29.app.user.IssuedToken;
import com.b.now._29.app.user.TokenService;
import com.b.now._29.app.user.TokenSubject;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 실제 application.yaml과 application-dev.yaml을 읽어 JWT_SECRET과 프로필별 설정을 확인한다.
 *
 * <p>{@code AuthCookies}가 가입·로그인, 연장, 로그아웃에 쓰는 {@code Set-Cookie} 헤더 값을 모두 만들므로, 쿠키 속성이 한 곳에서 같게 정해지는지도
 * 여기서 확인한다.
 */
class AuthConfigurationTest {

    private static final String SECRET_32_BYTES = "12345678901234567890123456789012";
    private static final String SECRET_31_BYTES = "1234567890123456789012345678901";
    private static final String DEV_PROFILE = "spring.profiles.active=dev";
    private static final TokenSubject SUBJECT = new TokenSubject(1L, 3);
    private static final IssuedToken TOKEN = new IssuedToken("token", Duration.ofHours(1));

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            // JwtConfig의 디코더가 Clock 빈으로 만료를 검사하고, AuthCookies가 TokenService로 토큰을 발급한다
            .withUserConfiguration(JwtConfig.class, ClockConfig.class, TokenService.class, AuthCookies.class);

    @Test
    @DisplayName("기본 프로필에서 JWT_SECRET이 없으면 시작하지 않는다")
    void failsWithoutSecret() {
        assumeThat(System.getenv("JWT_SECRET"))
                .as("환경 변수 JWT_SECRET이 없는 셸에서만 확인할 수 있다")
                .isNull();

        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", SECRET_31_BYTES})
    @DisplayName("기본 프로필에서 JWT_SECRET이 비었거나 32바이트보다 짧으면 HS256 키로 쓸 수 없으므로 시작하지 않는다")
    void failsWithShortSecret(String secret) {
        runner.withPropertyValues("JWT_SECRET=" + secret)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("JWT_SECRET이 정확히 32바이트면 시작한다")
    void startsWith32ByteSecret() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("길이는 글자 수가 아니라 UTF-8 바이트로 잰다: 한글 11자는 33바이트라 시작한다")
    void measuresSecretInBytes() {
        runner.withPropertyValues("JWT_SECRET=가나다라마바사아자차카")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("기본 프로필의 쿠키는 Secure다")
    void defaultProfileCookieIsSecure() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES).run(context -> {
            AuthCookies cookies = context.getBean(AuthCookies.class);

            assertThat(attributesOf(cookies.issue(TOKEN))).contains("Secure");
            assertThat(attributesOf(cookies.issueFor(SUBJECT))).contains("Secure");
            assertThat(attributesOf(cookies.expire())).contains("Secure");
        });
    }

    @Test
    @DisplayName("토큰보다 쿠키가 먼저 사라지거나 오래 남지 않도록, 쿠키의 Max-Age는 토큰 exp까지 남은 시간이다")
    void cookieMaxAgeFollowsToken() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES).run(context -> {
            AuthCookies cookies = context.getBean(AuthCookies.class);

            String header = cookies.issue(new IssuedToken("token", Duration.ofSeconds(1234)));

            assertThat(header).startsWith("access_token=token;");
            assertThat(attributesOf(header)).contains("Max-Age=1234", "HttpOnly", "SameSite=Lax", "Path=/api");
        });
    }

    @Test
    @DisplayName("회원 ID와 토큰 버전으로 만든 쿠키는 그 회원·버전의 1시간짜리 새 토큰이다")
    void issueForCreatesTokenCookie() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES).run(context -> {
            AuthCookies cookies = context.getBean(AuthCookies.class);
            TokenService tokenService = context.getBean(TokenService.class);

            String header = cookies.issueFor(SUBJECT);

            assertThat(header).startsWith("access_token=");
            assertThat(attributesOf(header)).contains("Max-Age=3600", "HttpOnly", "SameSite=Lax", "Path=/api");
            assertThat(tokenService.parse(valueOf(header))).contains(SUBJECT);
        });
    }

    @Test
    @DisplayName("브라우저가 발급한 쿠키를 지우려면 속성이 같아야 하므로, 만료 쿠키는 빈 값에 Max-Age=0이고 발급과 같은 속성이다")
    void expireCreatesEmptyCookie() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES).run(context -> {
            String header = context.getBean(AuthCookies.class).expire();

            assertThat(header).startsWith("access_token=;");
            assertThat(attributesOf(header)).contains("Max-Age=0", "HttpOnly", "SameSite=Lax", "Path=/api");
        });
    }

    @Test
    @DisplayName("요청 쿠키에서 access_token 값을 꺼내고, 없으면 빈 값이다")
    void tokenFromReadsAccessTokenCookie() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES).run(context -> {
            AuthCookies cookies = context.getBean(AuthCookies.class);
            MockHttpServletRequest withCookie = new MockHttpServletRequest();
            withCookie.setCookies(new Cookie("other", "x"), new Cookie("access_token", "abc"));
            MockHttpServletRequest withoutCookie = new MockHttpServletRequest();
            withoutCookie.setCookies(new Cookie("other", "x"));

            assertThat(cookies.tokenFrom(withCookie)).contains("abc");
            assertThat(cookies.tokenFrom(withoutCookie)).isEmpty();
            assertThat(cookies.tokenFrom(new MockHttpServletRequest())).isEmpty();
        });
    }

    @Test
    @DisplayName("로컬 http에서도 로그인되도록, dev 프로필은 JWT_SECRET 없이 시작하고 쿠키의 Secure를 끈다")
    void devProfileHasOwnSecretAndInsecureCookie() {
        runner.withPropertyValues(DEV_PROFILE).run(context -> {
            assertThat(context).hasNotFailed();
            AuthCookies cookies = context.getBean(AuthCookies.class);

            assertThat(attributesOf(cookies.issue(TOKEN))).doesNotContain("Secure");
            assertThat(attributesOf(cookies.issueFor(SUBJECT))).doesNotContain("Secure");
            assertThat(attributesOf(cookies.expire())).doesNotContain("Secure");
        });
    }

    /** {@code Set-Cookie} 값에서 {@code 이름=값} 뒤의 속성들. */
    private static List<String> attributesOf(String header) {
        List<String> parts = Arrays.asList(header.split(";\\s*"));
        return parts.subList(1, parts.size());
    }

    private static String valueOf(String header) {
        String nameValue = header.split(";", 2)[0];
        return nameValue.substring(nameValue.indexOf('=') + 1);
    }
}
