package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 토큰은 유효한데 회원 행이 DB에 없으면 모든 API에서 비회원으로 처리되고, 회원 행이 있으면 기존처럼 기록이 저장된다.
 *
 * <p>가입한 회원의 행을 DB에서 지워 그 상태를 만든다. 실제 보안 필터, Service, DB를 모두 연결한다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GhostUserTokenIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final String ADMIN = "/api/v1/admin";
    private static final String ME = "/api/v1/users/me";
    private static final String COOKIE_NAME = "access_token";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Cookie admin;
    private String slug;
    private long multipleChoiceId;
    private long choiceId;
    private long shortAnswerId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = uniqueEmail();
        admin = signUp(adminEmail);
        setRole(adminEmail, "ADMIN");
        slug = "ghost-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(json(
                        post(ADMIN + "/categories").cookie(admin),
                        "{\"slug\": \"%s\", \"name\": \"유령 토큰\", \"description\": null, \"sortOrder\": 6000}"
                                .formatted(slug)))
                .andExpect(status().isCreated());
        multipleChoiceId = createQuestion("""
                {"categorySlug": "%s", "type": "MULTIPLE_CHOICE", "content": "객관식", "explanation": "해설",
                 "modelAnswer": null, "sortOrder": 10,
                 "choices": [{"content": "정답", "correct": true}, {"content": "오답", "correct": false}]}
                """.formatted(slug));
        shortAnswerId = createQuestion("""
                {"categorySlug": "%s", "type": "SHORT_ANSWER", "content": "주관식", "explanation": null,
                 "modelAnswer": "모범 답안", "sortOrder": 20, "choices": []}
                """.formatted(slug));
        MvcResult question = mvc.perform(
                        get(ADMIN + "/questions/{id}", multipleChoiceId).cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        choiceId = ((Number) JsonPath.read(body(question), "$.choices[0].id")).longValue();
    }

    @Test
    @DisplayName("회원 행이 없는 토큰으로 답을 내면 200 채점 결과에 recorded false이고 풀이 기록을 저장하지 않는다")
    void answerOfGhostIsGradedButNotRecorded() throws Exception {
        Ghost ghost = ghost();

        postJson(
                        post("/api/v1/questions/{id}/answers", multipleChoiceId).cookie(ghost.cookie()),
                        "{\"choiceId\": " + choiceId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.recorded").value(false));

        assertThat(attemptCount(ghost.id())).isZero();
    }

    @Test
    @DisplayName("회원 행이 없는 토큰으로 전체·카테고리 랭킹을 보면 me가 null이다")
    void rankingMeOfGhostIsNull() throws Exception {
        Ghost ghost = ghost();

        mvc.perform(get("/api/v1/rankings").cookie(ghost.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(nullValue()));
        mvc.perform(get("/api/v1/categories/{slug}/rankings", slug).cookie(ghost.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(nullValue()));
    }

    @Test
    @DisplayName("회원 행이 없는 토큰으로 자기 채점을 저장하면 401이고 기록이 없다")
    void selfGradeOfGhostIsUnauthorized() throws Exception {
        Ghost ghost = ghost();

        expectUnauthorized(postJson(
                post("/api/v1/questions/{id}/self-grades", shortAnswerId).cookie(ghost.cookie()),
                "{\"correct\": true}"));

        assertThat(attemptCount(ghost.id())).isZero();
    }

    @Test
    @DisplayName("회원 행이 없는 토큰으로 /users/me와 하위 경로를 부르면 401이다")
    void meOfGhostIsUnauthorized() throws Exception {
        Ghost ghost = ghost();

        expectUnauthorized(mvc.perform(get(ME).cookie(ghost.cookie())));
        expectUnauthorized(mvc.perform(get(ME + "/attempts").cookie(ghost.cookie())));
        expectUnauthorized(mvc.perform(get(ME + "/category-stats").cookie(ghost.cookie())));
    }

    @Test
    @DisplayName("관리자였던 회원의 행이 없어진 토큰으로 관리자 API를 부르면 403이 아니라 401이다")
    void adminPathOfGhostIsUnauthorized() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);
        setRole(email, "ADMIN");
        long id = userId(cookie);
        deleteUser(id);

        expectUnauthorized(mvc.perform(get(ADMIN + "/categories").cookie(cookie)));
    }

    @Test
    @DisplayName("회원 행이 없고 남은 시간이 30분 미만인 토큰이면 공개 API도 새 쿠키를 주지 않는다")
    void nearExpiryTokenOfGhostIsNotRenewed() throws Exception {
        Ghost ghost = ghost();
        Instant now = Instant.now();
        Cookie nearExpiry = new Cookie(
                COOKIE_NAME,
                TestJwts.of(ghost.id(), now.minus(Duration.ofMinutes(50)), now.plus(Duration.ofMinutes(10))));

        mvc.perform(get("/api/v1/categories").cookie(nearExpiry))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        postJson(
                        post("/api/v1/questions/{id}/answers", multipleChoiceId).cookie(nearExpiry),
                        "{\"choiceId\": " + choiceId + "}")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get(ME).cookie(nearExpiry))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("회원 행이 있으면 같은 흐름에서 기록이 저장되고 랭킹 me가 있다")
    void existingMemberIsUnchanged() throws Exception {
        Cookie cookie = signUp(uniqueEmail());
        long id = userId(cookie);

        postJson(
                        post("/api/v1/questions/{id}/answers", multipleChoiceId).cookie(cookie),
                        "{\"choiceId\": " + choiceId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true));
        postJson(post("/api/v1/questions/{id}/self-grades", shortAnswerId).cookie(cookie), "{\"correct\": true}")
                .andExpect(status().isNoContent());

        assertThat(attemptCount(id)).isEqualTo(2);
        mvc.perform(get("/api/v1/categories/{slug}/rankings", slug).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.score").exists());
    }

    /** 가입 쿠키(유효, 1시간)를 받은 뒤 그 회원 행을 지운다. */
    private Ghost ghost() throws Exception {
        Cookie cookie = signUp(uniqueEmail());
        long id = userId(cookie);
        deleteUser(id);
        return new Ghost(id, cookie);
    }

    private record Ghost(long id, Cookie cookie) {}

    private void deleteUser(long id) {
        assertThat(jdbcTemplate.update("DELETE FROM users WHERE id = ?", id)).isEqualTo(1);
    }

    private long attemptCount(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM question_attempt WHERE user_id = ?", Long.class, userId);
    }

    private long createQuestion(String body) throws Exception {
        MvcResult result = mvc.perform(json(post(ADMIN + "/questions").cookie(admin), body))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private Cookie signUp(String email) throws Exception {
        MvcResult result = mvc.perform(json(
                        post("/api/v1/users").with(ClientIps.uniqueIp()),
                        "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"유령테스트\"}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        SetCookieHeader header = SetCookieHeader.from(result);
        return new Cookie(header.name(), header.value());
    }

    private long userId(Cookie cookie) throws Exception {
        MvcResult result =
                mvc.perform(get(ME).cookie(cookie)).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private void setRole(String email, String role) {
        jdbcTemplate.update("UPDATE users SET role = ? WHERE email = ?", role, email);
    }

    private ResultActions postJson(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(json(request, body));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static ResultActions expectUnauthorized(ResultActions actions) throws Exception {
        return actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String uniqueEmail() {
        return "ghost-it-" + UUID.randomUUID() + "@example.com";
    }
}
