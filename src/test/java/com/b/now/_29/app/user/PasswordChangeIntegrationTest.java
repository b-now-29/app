package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 비밀번호 변경 (docs/features/password-change.md)을 실제 보안 필터, Service, DB, 운영 제한 수치 (회원별 15분 5회)로 확인한다.
 *
 * <p>두 기기는 같은 회원으로 따로 로그인해 받은 두 쿠키로 나타낸다. 로그인·가입 제한과 섞이지 않도록 그 요청은 매번 새 IP로 보낸다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordChangeIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final String NEW_PASSWORD = "new-password-123";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final String ME = "/api/v1/users/me";
    private static final String PASSWORD_PATH = "/api/v1/users/me/password";
    private static final String COOKIE_NAME = "access_token";
    private static final String WRONG_CURRENT_PASSWORD = "현재 비밀번호가 맞지 않습니다.";
    private static final int PASSWORD_CHANGE_MAX = 5;
    private static final long WINDOW_SECONDS = 15 * 60;

    // BCrypt 비교·해시가 불릴 때의 트랜잭션 상태를 기록하려고 운영 인코더를 감싼다 (D3)
    @MockitoSpyBean
    PasswordEncoder passwordEncoder;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TokenService tokenService;

    @Test
    @DisplayName("바꾸면 두 기기의 옛 토큰은 401, 응답의 새 쿠키는 200이고, 새 비밀번호로만 로그인된다 (R1, R4, R5)")
    void changeRevokesOtherDevicesAndKeepsThisDevice() throws Exception {
        String email = uniqueEmail();
        signUp(email);
        Cookie laptop = login(email, PASSWORD);
        Cookie phone = login(email, PASSWORD);
        long userId = userId(laptop);

        MvcResult result = changePassword(laptop, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        Cookie renewed = cookieFrom(result);
        assertThat(tokenService.parse(renewed.getValue())).contains(new TokenSubject(userId, 1));
        assertThat(tokenVersion(email)).isEqualTo(1);

        mvc.perform(get(ME).cookie(laptop)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).cookie(phone)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).cookie(renewed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        loginRequest(email, PASSWORD).andExpect(status().isUnauthorized());
        loginRequest(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재와 같은 비밀번호로 바꿔도 204이고 다른 기기의 토큰은 무효가 된다 (D7)")
    void changeToSamePasswordStillRevokes() throws Exception {
        String email = uniqueEmail();
        Cookie laptop = signUp(email);
        Cookie phone = login(email, PASSWORD);

        MvcResult result = changePassword(laptop, PASSWORD, PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(get(ME).cookie(phone)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).cookie(cookieFrom(result))).andExpect(status().isOk());
        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400과 R2 문구이고, 비밀번호·토큰 버전이 그대로라 기존 쿠키와 옛 비밀번호가 계속 된다 (R2, D1)")
    void wrongCurrentPasswordChangesNothing() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);

        changePassword(cookie, WRONG_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(WRONG_CURRENT_PASSWORD))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(tokenVersion(email)).isZero();
        mvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk());
        loginRequest(email, PASSWORD).andExpect(status().isOk());
        loginRequest(email, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("남은 시간이 30분 미만인 쿠키로 바꿔도 Set-Cookie는 새 버전 쿠키 하나뿐이다 (R5, D8)")
    void nearExpiryCookieGetsOnlyNewVersionCookie() throws Exception {
        String email = uniqueEmail();
        long userId = userId(signUp(email));
        Cookie nearExpiry = new Cookie(COOKIE_NAME, TestJwts.nearExpiry(userId, User.INITIAL_TOKEN_VERSION));

        MvcResult result = changePassword(nearExpiry, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        Cookie renewed = cookieFrom(result);
        assertThat(tokenService.parse(renewed.getValue())).contains(new TokenSubject(userId, 1));
        mvc.perform(get(ME).cookie(renewed)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("남은 시간이 30분 미만인 쿠키로 실패하면(400·429) 다른 API처럼 같은 버전의 연장 쿠키 하나를 받고, 그 쿠키로 계속 쓸 수 있다 (R5, D8)")
    void failedChangeWithNearExpiryCookieIsRenewed() throws Exception {
        String email = uniqueEmail();
        long userId = userId(signUp(email));
        Cookie nearExpiry = new Cookie(COOKIE_NAME, TestJwts.nearExpiry(userId, User.INITIAL_TOKEN_VERSION));

        MvcResult wrong = changePassword(nearExpiry, WRONG_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(WRONG_CURRENT_PASSWORD))
                .andReturn();

        assertThat(wrong.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        Cookie renewed = cookieFrom(wrong);
        assertThat(tokenService.parse(renewed.getValue()))
                .contains(new TokenSubject(userId, User.INITIAL_TOKEN_VERSION));
        mvc.perform(get(ME).cookie(renewed)).andExpect(status().isOk());

        for (int i = 1; i < PASSWORD_CHANGE_MAX; i++) {
            changePassword(nearExpiry, WRONG_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        MvcResult blocked = changePassword(nearExpiry, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(blocked.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        assertThat(tokenService.parse(cookieFrom(blocked).getValue()))
                .contains(new TokenSubject(userId, User.INITIAL_TOKEN_VERSION));
        assertThat(tokenVersion(email)).isZero();
    }

    @Test
    @DisplayName("NUL 문자가 든 새 비밀번호는 400이고 아무것도 바뀌지 않는다 (R3, D11)")
    void nulInNewPasswordChangesNothing() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);

        changePassword(cookie, PASSWORD, "new-pass\\u0000word").andExpect(status().isBadRequest());

        assertThat(tokenVersion(email)).isZero();
        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("NUL 문자가 든 비밀번호로는 가입할 수 없다 (D11)")
    void nulInSignUpPasswordIsRejected() throws Exception {
        postJson(
                        "/api/v1/users",
                        "{\"email\": \"%s\", \"password\": \"pass\\u0000word1234\", \"nickname\": \"비번변경\"}"
                                .formatted(uniqueEmail()))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("같은 회원이 5번 틀리면 6번째는 맞아도 429와 Retry-After이고 비밀번호는 그대로다. 다른 회원은 바꿀 수 있다 (R7, D2)")
    void sixthAttemptIsTooManyRequests() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);
        for (int i = 0; i < PASSWORD_CHANGE_MAX; i++) {
            changePassword(cookie, WRONG_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        MvcResult blocked = changePassword(cookie, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        long retryAfter = Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(1L, WINDOW_SECONDS);
        assertThat(tokenVersion(email)).isZero();
        loginRequest(email, PASSWORD).andExpect(status().isOk());

        Cookie other = signUp(uniqueEmail());
        changePassword(other, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("맞는 현재 비밀번호로 바꾸면 그 회원의 횟수가 지워져, 다시 4번 틀려도 막히지 않는다 (R7)")
    void successResetsAttempts() throws Exception {
        Cookie cookie = signUp(uniqueEmail());
        for (int i = 0; i < PASSWORD_CHANGE_MAX - 1; i++) {
            changePassword(cookie, WRONG_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        Cookie renewed = cookieFrom(changePassword(cookie, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn());

        for (int i = 0; i < PASSWORD_CHANGE_MAX - 1; i++) {
            changePassword(renewed, WRONG_PASSWORD, PASSWORD).andExpect(status().isBadRequest());
        }

        changePassword(renewed, NEW_PASSWORD, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("새 비밀번호가 가입 규칙을 어기면 400이고 아무것도 바뀌지 않는다 (R3)")
    void invalidNewPasswordChangesNothing() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);

        changePassword(cookie, PASSWORD, "short").andExpect(status().isBadRequest());

        assertThat(tokenVersion(email)).isZero();
        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비회원이면 401이다 (R6)")
    void guestIsUnauthorized() throws Exception {
        mvc.perform(put(PASSWORD_PATH).contentType(MediaType.APPLICATION_JSON).content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("현재 비밀번호 비교와 새 비밀번호 해시는 DB 트랜잭션 밖에서 하고, 저장은 커밋된다 (D3)")
    void bcryptRunsOutsideTransaction() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);
        List<String> calls = new CopyOnWriteArrayList<>();
        willAnswer(invocation -> {
                    calls.add("matches:" + TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .given(passwordEncoder)
                .matches(any(), any());
        willAnswer(invocation -> {
                    calls.add("encode:" + TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .given(passwordEncoder)
                .encode(any());

        changePassword(cookie, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(calls).containsExactlyInAnyOrder("matches:false", "encode:false");
        assertThat(tokenVersion(email)).isEqualTo(1);
    }

    private ResultActions changePassword(Cookie cookie, String current, String newPassword) throws Exception {
        return mvc.perform(put(PASSWORD_PATH)
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(current, newPassword)));
    }

    private static String body(String current, String newPassword) {
        return "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, newPassword);
    }

    private int tokenVersion(String email) {
        return jdbcTemplate.queryForObject("SELECT token_version FROM users WHERE email = ?", Integer.class, email);
    }

    private Cookie signUp(String email) throws Exception {
        return cookieFrom(postJson(
                        "/api/v1/users",
                        "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"비번변경\"}".formatted(email, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Cookie login(String email, String password) throws Exception {
        return cookieFrom(
                loginRequest(email, password).andExpect(status().isOk()).andReturn());
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
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
        return "password-it-" + UUID.randomUUID() + "@example.com";
    }
}
