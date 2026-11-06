package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.controller.PingController;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.AuthController;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserController;
import com.b.now._29.app.user.UserNotFoundException;
import com.b.now._29.app.user.UserResponse;
import com.b.now._29.app.user.UserService;
import jakarta.servlet.http.Cookie;
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
                arguments("다른 키로 서명", TestJwts.signedWithOtherKey(USER_ID)),
                arguments("본문 변조", TestJwts.tampered(USER_ID, 2L)),
                arguments("서명 없음 (alg: none)", TestJwts.unsigned(USER_ID)),
                arguments("숫자가 아닌 sub", TestJwts.withNonNumericSubject()),
                arguments("JWT 형식이 아님", "not-a-jwt"),
                arguments("빈 값", ""));
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
        given(userService.login(EMAIL, "password1234")).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER));

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
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER));

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
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER));

        MvcResult result = mvc.perform(get(ME).cookie(accessToken(TestJwts.valid(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static Cookie accessToken(String token) {
        return new Cookie(COOKIE_NAME, token);
    }

    private static void expectUnauthorized(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").exists());
    }
}
