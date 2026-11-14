package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.controller.PingController;
import com.b.now._29.app.support.SetCookieHeader;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.AuthController;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserController;
import com.b.now._29.app.user.UserNotFoundException;
import com.b.now._29.app.user.UserResponse;
import com.b.now._29.app.user.UserService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 보안 설정과 필터로 쿠키 상태별 동작을 확인한다 (R6, R8, R9, D3~D6). */
@WebMvcTest({PingController.class, UserController.class, AuthController.class})
@WithAppSecurity
@Import(SpaConfig.class)
class AccessTokenSecurityTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String CREDENTIALS = "{\"email\": \"user@example.com\", \"password\": \"password1234\"}";
    private static final String ME = "/api/v1/users/me";
    private static final String COOKIE_NAME = "access_token";
    private static final String SPA_INDEX_MARKER = "SPA_INDEX";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                arguments("만료", TestJwts.expired(USER_ID)),
                arguments(
                        "exp가 1초 지남 (R10)",
                        TestJwts.of(
                                USER_ID,
                                Instant.now().minus(Duration.ofHours(1)).minusSeconds(1),
                                Instant.now().minusSeconds(1))),
                arguments("헤더 typ이 at+jwt (R13)", TestJwts.withType(USER_ID, "at+jwt")),
                arguments("다른 키로 서명", TestJwts.signedWithOtherKey(USER_ID)),
                arguments("본문 변조", TestJwts.tampered(USER_ID, 2L)),
                arguments("서명 없음 (alg: none)", TestJwts.unsigned(USER_ID)),
                arguments("숫자가 아닌 sub", TestJwts.withNonNumericSubject()),
                arguments("auth_time 없음 (배포 전 7일 토큰)", TestJwts.withoutAuthTime(USER_ID)),
                arguments(
                        "auth_time이 1일보다 오래됨",
                        TestJwts.of(
                                USER_ID,
                                Instant.now().minus(Duration.ofDays(1)).minusSeconds(1),
                                Instant.now().plus(Duration.ofMinutes(20)))),
                arguments("JWT 형식이 아님", "not-a-jwt"),
                arguments("빈 값", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 쿠키로 공개 API를 부르면 새 쿠키를 내려 주지 않는다 (token-sliding-expiry R7)")
    void invalidCookieIsNotRenewed(String description, String token) throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(accessToken(token)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // 자동 연장 (token-sliding-expiry R2, R3, R8, D1, D7)

    @Test
    @DisplayName("남은 시간이 30분 미만인 쿠키로 /users/me를 부르면 200과 함께 1시간짜리 새 쿠키를 받는다 (R2, R3)")
    void meRenewsTokenNearExpiry() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 1));
        Instant authTime = Instant.now().minus(Duration.ofMinutes(50)).truncatedTo(ChronoUnit.SECONDS);

        MvcResult result = mvc.perform(get(ME).cookie(accessToken(nearExpiry(authTime))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        SetCookieHeader cookie = SetCookieHeader.from(result);
        assertThat(cookie.name()).isEqualTo(COOKIE_NAME);
        assertThat(cookie.attributes()).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api");
        // 시계가 요청 중에 흘러가므로 1초 차이는 허용한다
        assertThat(cookie.attributes()).containsAnyOf("Max-Age=3600", "Max-Age=3599");

        Jwt renewed = decoder().decode(cookie.value());
        assertThat(renewed.getSubject()).isEqualTo(String.valueOf(USER_ID));
        assertThat(((Number) renewed.getClaims().get(TestJwts.AUTH_TIME)).longValue())
                .isEqualTo(authTime.getEpochSecond());
    }

    @Test
    @DisplayName("공개 API도 남은 시간이 30분 미만인 쿠키면 새 쿠키를 받는다 (R2)")
    void publicApiRenewsTokenNearExpiry() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/ping")
                        .cookie(accessToken(nearExpiry(Instant.now().minus(Duration.ofMinutes(50))))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(SetCookieHeader.from(result).name()).isEqualTo(COOKIE_NAME);
    }

    @Test
    @DisplayName("남은 시간이 30분 이상이면 새 쿠키를 내려 주지 않는다 (R2, D2)")
    void freshCookieIsNotRenewed() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 1));
        Instant now = Instant.now();
        String token = TestJwts.of(USER_ID, now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(50)));

        mvc.perform(get(ME).cookie(accessToken(token)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("남은 시간이 30분 미만인 쿠키로 로그아웃해도 Set-Cookie는 만료 쿠키 하나뿐이다 (R8, D7)")
    void logoutDoesNotRenew() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout")
                        .cookie(accessToken(nearExpiry(Instant.now().minus(Duration.ofMinutes(50))))))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        SetCookieHeader cookie = SetCookieHeader.from(result);
        assertThat(cookie.value()).isEmpty();
        assertThat(cookie.attributes()).contains("Max-Age=0");
    }

    @Test
    @DisplayName("exp가 1초 지난 쿠키로 /users/me를 부르면 401이고 새 쿠키를 주지 않는다 (R10: 허용 오차 0)")
    void tokenExpiredOneSecondAgoIsGuest() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = TestJwts.of(USER_ID, now.minus(Duration.ofHours(1)).minusSeconds(1), now.minusSeconds(1));

        expectUnauthorized(mvc.perform(get(ME).cookie(accessToken(token))))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    /** 남은 시간이 10분인 정상 토큰. */
    private static String nearExpiry(Instant authTime) {
        return TestJwts.of(USER_ID, authTime, Instant.now().plus(Duration.ofMinutes(10)));
    }

    private static JwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(TestJwts.key())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 쿠키를 보내도 공개 API는 정상 응답한다 (R9)")
    void publicApiIgnoresInvalidCookie(String description, String token) throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(accessToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 쿠키로 /users/me를 부르면 401 ProblemDetail이다 (R9)")
    void meRejectsInvalidCookie(String description, String token) throws Exception {
        expectUnauthorized(mvc.perform(get(ME).cookie(accessToken(token))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 쿠키가 남아 있어도 다시 로그인할 수 있다 (R9)")
    void loginWorksWithInvalidCookie(String description, String token) throws Exception {
        given(userService.login(eq(EMAIL), eq("password1234"), anyString()))
                .willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 1));

        mvc.perform(post("/api/v1/auth/login")
                        .cookie(accessToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 쿠키가 남아 있어도 로그아웃은 204다")
    void logoutWorksWithInvalidCookie(String description, String token) throws Exception {
        mvc.perform(post("/api/v1/auth/logout").cookie(accessToken(token))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("쿠키 없이 /users/me를 부르면 401 ProblemDetail이다 (R6, D6)")
    void meWithoutCookieIsUnauthorized() throws Exception {
        expectUnauthorized(mvc.perform(get(ME)));
    }

    @Test
    @DisplayName("유효한 쿠키면 /users/me는 200이고 토큰의 sub로 사용자를 찾는다 (R6, D2)")
    void meWithValidCookie() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 1));

        mvc.perform(get(ME).cookie(accessToken(TestJwts.valid(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("유효한 쿠키의 사용자가 DB에 없으면 /users/me는 401이다 (R6)")
    void meOfDeletedUserIsUnauthorized() throws Exception {
        given(userService.getUser(USER_ID)).willThrow(new UserNotFoundException(USER_ID));

        expectUnauthorized(mvc.perform(get(ME).cookie(accessToken(TestJwts.valid(USER_ID)))));
    }

    @Test
    @DisplayName("쿠키 없이 공개 API를 쓸 수 있다 (R8, D4)")
    void publicApiWithoutCookie() throws Exception {
        mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 쿠키로도 공개 API를 쓸 수 있다")
    void publicApiWithValidCookie() throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(accessToken(TestJwts.valid(USER_ID))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/signup", "/categories/java", "/about"})
    @DisplayName("화면 경로는 인증 없이 index.html을 돌려주고, Spring Security 기본 로그인 페이지가 가로채지 않는다 (D4)")
    void spaRoutesArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(containsString(SPA_INDEX_MARKER)));
    }

    @Test
    @DisplayName("CSRF 토큰 없이 POST할 수 있다 (D5)")
    void postDoesNotRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("세션을 만들지 않는다 (D5)")
    void doesNotCreateSession() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 1));

        MvcResult result = mvc.perform(get(ME).cookie(accessToken(TestJwts.valid(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static Cookie accessToken(String token) {
        return new Cookie(COOKIE_NAME, token);
    }

    private static ResultActions expectUnauthorized(ResultActions actions) throws Exception {
        return actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").exists());
    }
}
