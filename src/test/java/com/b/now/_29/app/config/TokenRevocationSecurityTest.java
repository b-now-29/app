package com.b.now._29.app.config;

import static com.b.now._29.app.support.ExpiredAuthCookie.assertOnlyExpiredCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.b.now._29.app.user.TokenSubject;
import com.b.now._29.app.user.TokenUserVerifier;
import com.b.now._29.app.user.UserController;
import com.b.now._29.app.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 토큰의 버전이 회원의 지금 버전과 다르면 그 요청은 비회원으로 처리되고 토큰도 연장되지 않는다. 로그아웃은 인증된 회원의 ID를, 비회원이면 회원 ID 없이 Service에
 * 넘긴다.
 *
 * <p>회원 ID와 버전이 지금 유효한지는 {@code @WithAppSecurity}가 넣는 {@code TokenUserVerifier} mock으로 정한다. 회원 1의 지금 버전을 3으로
 * 두고, 버전 2 토큰을 로그아웃 전에 받은 토큰으로 쓴다.
 */
@WebMvcTest({PingController.class, UserController.class, AuthController.class})
@WithAppSecurity
class TokenRevocationSecurityTest {

    private static final long USER_ID = 1L;
    private static final int CURRENT_VERSION = 3;
    private static final int REVOKED_VERSION = 2;
    private static final String ME = "/api/v1/users/me";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String COOKIE_NAME = "access_token";

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenUserVerifier tokenUserVerifier;

    @MockitoBean
    UserService userService;

    @BeforeEach
    void setUp() {
        given(tokenUserVerifier.isCurrent(new TokenSubject(USER_ID, CURRENT_VERSION)))
                .willReturn(true);
        given(tokenUserVerifier.isCurrent(new TokenSubject(USER_ID, REVOKED_VERSION)))
                .willReturn(false);
    }

    // 버전이 다른 토큰

    @Test
    @DisplayName("버전이 다른 토큰으로 /users/me를 부르면 401이고 회원 조회까지 가지 않는다")
    void meWithRevokedTokenIsUnauthorized() throws Exception {
        expectUnauthorized(mvc.perform(get(ME).cookie(cookie(TestJwts.withVersion(USER_ID, REVOKED_VERSION)))));

        then(tokenUserVerifier).should().isCurrent(new TokenSubject(USER_ID, REVOKED_VERSION));
        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("버전이 다른 토큰으로도 공개 API는 200이다")
    void publicApiWithRevokedTokenIsOk() throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(cookie(TestJwts.withVersion(USER_ID, REVOKED_VERSION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("버전이 다르고 남은 시간이 30분 미만인 토큰이면 공개 API와 /users/me 모두 Set-Cookie가 없다")
    void revokedNearExpiryTokenIsNotRenewed() throws Exception {
        Cookie nearExpiry = cookie(TestJwts.nearExpiry(USER_ID, REVOKED_VERSION));

        mvc.perform(get("/api/v1/ping").cookie(nearExpiry))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        expectUnauthorized(mvc.perform(get(ME).cookie(nearExpiry)))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("지금 버전의 토큰은 인증되고, 남은 시간이 30분 미만이면 같은 버전의 새 쿠키를 받는다")
    void currentVersionTokenIsAuthenticatedAndRenewed() throws Exception {
        MvcResult result = mvc.perform(
                        get("/api/v1/ping").cookie(cookie(TestJwts.nearExpiry(USER_ID, CURRENT_VERSION))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        clearInvocations(tokenUserVerifier, userService);
        mvc.perform(get(ME).cookie(cookie(SetCookieHeader.from(result).value())))
                .andExpect(status().isOk());

        then(tokenUserVerifier).should().isCurrent(new TokenSubject(USER_ID, CURRENT_VERSION));
        then(userService).should().getUser(USER_ID);
    }

    @Test
    @DisplayName("ver 클레임이 없는 토큰은 회원을 확인하지 않고 비회원이다: /users/me 401, 공개 API 200, 연장 없음")
    void tokenWithoutVersionIsGuest() throws Exception {
        Cookie legacy = cookie(TestJwts.nearExpiryWithoutVersion(USER_ID));

        expectUnauthorized(mvc.perform(get(ME).cookie(legacy)))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get("/api/v1/ping").cookie(legacy))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        then(tokenUserVerifier).should(never()).isCurrent(any());
        then(userService).shouldHaveNoInteractions();
    }

    // 로그아웃: Controller는 인증 여부로 분기하지 않고, 비회원이면 회원 ID 없이 Service에 넘긴다

    @Test
    @DisplayName("지금 버전의 토큰으로 로그아웃하면 그 회원 ID로 Service를 부르고 204와 만료 쿠키 하나만 받는다")
    void logoutWithCurrentTokenRevokes() throws Exception {
        MvcResult result = mvc.perform(post(LOGOUT).cookie(cookie(TestJwts.nearExpiry(USER_ID, CURRENT_VERSION))))
                .andExpect(status().isNoContent())
                .andReturn();

        assertOnlyExpiredCookie(result);
        then(userService).should().logout(USER_ID);
    }

    @Test
    @DisplayName("토큰 없이 로그아웃하면 회원 ID 없이 Service를 부르고 204와 만료 쿠키를 받는다")
    void logoutWithoutTokenPassesNoUser() throws Exception {
        MvcResult result =
                mvc.perform(post(LOGOUT)).andExpect(status().isNoContent()).andReturn();

        assertOnlyExpiredCookie(result);
        then(userService).should().logout(null);
        then(userService).should(never()).logout(USER_ID);
    }

    @Test
    @DisplayName("이미 무효가 된 버전의 토큰으로 로그아웃하면 회원 ID 없이 Service를 부르고 204와 만료 쿠키를 받는다")
    void logoutWithRevokedTokenPassesNoUser() throws Exception {
        MvcResult result = mvc.perform(post(LOGOUT).cookie(cookie(TestJwts.withVersion(USER_ID, REVOKED_VERSION))))
                .andExpect(status().isNoContent())
                .andReturn();

        assertOnlyExpiredCookie(result);
        then(userService).should().logout(null);
        then(userService).should(never()).logout(USER_ID);
    }

    @Test
    @DisplayName("만료·위조·ver 없는 토큰으로 로그아웃하면 회원 ID 없이 Service를 부르고 204와 만료 쿠키를 받는다")
    void logoutWithInvalidTokenPassesNoUser() throws Exception {
        String[] tokens = {
            TestJwts.expired(USER_ID),
            TestJwts.signedWithOtherKey(USER_ID),
            TestJwts.withoutVersion(USER_ID),
            "not-a-jwt"
        };
        for (String token : tokens) {
            MvcResult result = mvc.perform(post(LOGOUT).cookie(cookie(token)))
                    .andExpect(status().isNoContent())
                    .andReturn();

            assertOnlyExpiredCookie(result);
        }
        then(userService).should(times(tokens.length)).logout(null);
        then(userService).should(never()).logout(USER_ID);
    }

    private static Cookie cookie(String token) {
        return new Cookie(COOKIE_NAME, token);
    }

    private static ResultActions expectUnauthorized(ResultActions actions) throws Exception {
        return actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
