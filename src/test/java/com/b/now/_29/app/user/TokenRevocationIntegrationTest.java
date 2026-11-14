package com.b.now._29.app.user;

import static com.b.now._29.app.support.ExpiredAuthCookie.assertOnlyExpiredCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.ClientIps;
import com.b.now._29.app.support.SetCookieHeader;
import com.b.now._29.app.support.TestJwts;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 로그아웃하면 그 회원의 모든 기기 토큰이 서버에서 무효가 된다. 실제 보안 필터, Service, DB를 모두 연결한다.
 *
 * <p>두 기기는 같은 회원으로 따로 로그인해 받은 두 쿠키로 나타낸다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenRevocationIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final String ME = "/api/v1/users/me";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String COOKIE_NAME = "access_token";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("한 기기에서 로그아웃하면 두 기기의 토큰 모두 /users/me가 401이고, 다시 로그인한 토큰은 200이다")
    void logoutRevokesTokensOfAllDevices() throws Exception {
        String email = uniqueEmail();
        signUp(email);
        Cookie laptop = login(email);
        Cookie phone = login(email);
        mvc.perform(get(ME).cookie(laptop)).andExpect(status().isOk());
        mvc.perform(get(ME).cookie(phone)).andExpect(status().isOk());

        MvcResult logout = mvc.perform(post(LOGOUT).cookie(laptop))
                .andExpect(status().isNoContent())
                .andReturn();

        assertOnlyExpiredCookie(logout);
        mvc.perform(get(ME).cookie(laptop)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).cookie(phone)).andExpect(status().isUnauthorized());

        Cookie again = login(email);
        mvc.perform(get(ME).cookie(again))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get(ME).cookie(phone)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃하면 그 회원의 token_version만 1 오르고 다른 회원은 그대로다")
    void logoutIncrementsOnlyThatUsersVersion() throws Exception {
        String email = uniqueEmail();
        String otherEmail = uniqueEmail();
        Cookie cookie = signUp(email);
        Cookie other = signUp(otherEmail);
        assertThat(tokenVersion(email)).isZero();

        mvc.perform(post(LOGOUT).cookie(cookie)).andExpect(status().isNoContent());

        assertThat(tokenVersion(email)).isEqualTo(1);
        assertThat(tokenVersion(otherEmail)).isZero();
        mvc.perform(get(ME).cookie(other)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다시 로그인한 뒤 또 로그아웃하면 버전이 2가 되고 그 토큰도 401이다")
    void secondLogoutRevokesReissuedToken() throws Exception {
        String email = uniqueEmail();
        Cookie first = signUp(email);
        mvc.perform(post(LOGOUT).cookie(first)).andExpect(status().isNoContent());
        Cookie second = login(email);

        mvc.perform(post(LOGOUT).cookie(second)).andExpect(status().isNoContent());

        assertThat(tokenVersion(email)).isEqualTo(2);
        mvc.perform(get(ME).cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰 없이 로그아웃하면 204와 만료 쿠키이고 버전은 그대로다")
    void logoutWithoutTokenKeepsVersion() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);

        MvcResult logout =
                mvc.perform(post(LOGOUT)).andExpect(status().isNoContent()).andReturn();

        assertOnlyExpiredCookie(logout);
        assertThat(tokenVersion(email)).isZero();
        mvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 무효가 된 토큰으로 다시 로그아웃하면 204와 만료 쿠키이고 버전은 더 오르지 않는다")
    void logoutWithRevokedTokenKeepsVersion() throws Exception {
        String email = uniqueEmail();
        Cookie revoked = signUp(email);
        mvc.perform(post(LOGOUT).cookie(revoked)).andExpect(status().isNoContent());
        Cookie current = login(email);

        MvcResult logout = mvc.perform(post(LOGOUT).cookie(revoked))
                .andExpect(status().isNoContent())
                .andReturn();

        assertOnlyExpiredCookie(logout);
        assertThat(tokenVersion(email)).isEqualTo(1);
        mvc.perform(get(ME).cookie(current)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("무효가 된 토큰은 공개 API에서 200이지만 남은 시간이 30분 미만이어도 새 쿠키를 받지 못한다")
    void revokedNearExpiryTokenIsNotRenewed() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);
        long userId = userId(cookie);
        Cookie nearExpiry = new Cookie(COOKIE_NAME, TestJwts.nearExpiry(userId));
        mvc.perform(post(LOGOUT).cookie(cookie)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/categories").cookie(nearExpiry))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get(ME).cookie(nearExpiry))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("ver 클레임이 없는 토큰은 회원이 있어도 /users/me가 401이고 새 쿠키를 받지 못한다")
    void tokenWithoutVersionIsGuest() throws Exception {
        long userId = userId(signUp(uniqueEmail()));
        Cookie cookie = new Cookie(COOKIE_NAME, TestJwts.nearExpiryWithoutVersion(userId));

        mvc.perform(get(ME).cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get("/api/v1/categories").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private int tokenVersion(String email) {
        return jdbcTemplate.queryForObject("SELECT token_version FROM users WHERE email = ?", Integer.class, email);
    }

    private Cookie signUp(String email) throws Exception {
        return cookieFrom(postJson(
                        "/api/v1/users",
                        "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"로그아웃\"}".formatted(email, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Cookie login(String email) throws Exception {
        return cookieFrom(
                postJson("/api/v1/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    private long userId(Cookie cookie) throws Exception {
        MvcResult result =
                mvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"))
                .longValue();
    }

    /** 가입·로그인 제한 횟수가 다른 테스트와 섞이지 않도록 요청마다 다른 IP로 보낸다. */
    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path)
                .with(ClientIps.uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Cookie cookieFrom(MvcResult result) {
        SetCookieHeader header = SetCookieHeader.from(result);
        assertThat(header.name()).isEqualTo(COOKIE_NAME);
        return new Cookie(header.name(), header.value());
    }

    private static String uniqueEmail() {
        return "revoke-it-" + UUID.randomUUID() + "@example.com";
    }
}
