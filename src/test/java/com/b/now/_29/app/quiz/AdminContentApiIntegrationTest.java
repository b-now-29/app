package com.b.now._29.app.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 실제 보안 필터, Service, DB로 관리자 카테고리·문제 관리와 공개 API의 삭제 처리를 확인한다 (R2~R8, D2, D3, D6, D7). */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminContentApiIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final String ADMIN = "/api/v1/admin";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Cookie admin;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        String email = uniqueEmail();
        admin = signUp(email);
        setRole(email, "ADMIN");
        slug = "it-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("가입한 회원은 USER이고, DB에서 role을 바꾸면 같은 쿠키로도 바로 반영된다 (R1, R3, D3)")
    void roleChangeTakesEffectImmediately() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = signUp(email);

        mvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get(ADMIN + "/categories").cookie(cookie)).andExpect(status().isForbidden());

        setRole(email, "ADMIN");
        mvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get(ADMIN + "/categories").cookie(cookie)).andExpect(status().isOk());

        setRole(email, "USER");
        mvc.perform(get(ADMIN + "/categories").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("관리자가 회원 관리 API로 role을 바꾸면 DB에 저장되어 그 회원의 같은 쿠키에 바로 반영되고, 자기 해제는 409다 (회원 관리 R1~R4)")
    void roleChangeApiPersistsAndTakesEffectImmediately() throws Exception {
        Cookie member = signUp(uniqueEmail());
        long memberId = userId(member);
        String rolePath = ADMIN + "/users/" + memberId + "/role";

        mvc.perform(asAdmin(get(ADMIN + "/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].createdAt".formatted(memberId))
                        .value(hasItem(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"))));

        mvc.perform(asAdmin(json(put(rolePath), "{\"role\": \"ADMIN\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get(ADMIN + "/users").cookie(member)).andExpect(status().isOk());

        mvc.perform(asAdmin(json(put(rolePath), "{\"role\": \"USER\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get(ADMIN + "/users").cookie(member)).andExpect(status().isForbidden());

        mvc.perform(asAdmin(json(put(ADMIN + "/users/" + userId(admin) + "/role"), "{\"role\": \"USER\"}")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(asAdmin(get(ADMIN + "/users"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비회원은 관리자 API에서 401이다 (R2)")
    void guestIsUnauthorized() throws Exception {
        mvc.perform(get(ADMIN + "/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("관리자가 만든 카테고리·문제가 공개 API에 보이고, 정답 정보는 관리자 API에만 있다 (R4, R5, R8)")
    void createdContentIsPublicWithoutAnswers() throws Exception {
        createCategory(slug).andExpect(status().isCreated());
        long questionId = createMultipleChoice(slug);

        mvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].slug", hasItem(slug)));
        mvc.perform(get("/api/v1/categories/{slug}/questions", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(questionId))
                .andExpect(jsonPath("$[*].modelAnswer").isEmpty())
                .andExpect(jsonPath("$[*].choices[*].correct").isEmpty());

        mvc.perform(asAdmin(get(ADMIN + "/questions/{id}", questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorySlug").value(slug))
                .andExpect(jsonPath("$.choices[*].correct", hasItem(true)));
        mvc.perform(asAdmin(get(ADMIN + "/categories")))
                .andExpect(jsonPath("$[?(@.slug == '%s')].questionCount".formatted(slug), hasItem(1)));
    }

    @Test
    @DisplayName("유형별 규칙을 어긴 문제는 400 ProblemDetail이고 저장되지 않는다 (R5)")
    void rejectsQuestionBreakingTypeRule() throws Exception {
        createCategory(slug).andExpect(status().isCreated());

        mvc.perform(asAdmin(json(post(ADMIN + "/questions"), """
                        {"categorySlug": "%s", "type": "MULTIPLE_CHOICE", "content": "보기 하나", "sortOrder": 0,
                         "choices": [{"content": "A", "correct": true}]}
                        """.formatted(slug))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(asAdmin(json(post(ADMIN + "/questions"), """
                        {"categorySlug": "%s", "type": "SHORT_ANSWER", "content": "답 없음", "modelAnswer": "  ",
                         "sortOrder": 0, "choices": []}
                        """.formatted(slug))))
                .andExpect(status().isBadRequest());

        mvc.perform(asAdmin(get(ADMIN + "/questions").queryParam("category", slug)))
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("삭제한 문제는 공개 목록에서 빠지고 답 제출·모범 답안·자기 채점이 404이며, 남은 풀이 기록은 내 기록에 보인다 (R6)")
    void deletedQuestionIsGoneFromPublicApi() throws Exception {
        createCategory(slug).andExpect(status().isCreated());
        long multipleChoiceId = createMultipleChoice(slug);
        long shortAnswerId = createShortAnswer(slug);
        long choiceId = firstChoiceId(multipleChoiceId);
        Cookie member = signUp(uniqueEmail());
        postJson(
                        post("/api/v1/questions/{id}/answers", multipleChoiceId).cookie(member),
                        "{\"choiceId\": " + choiceId + "}")
                .andExpect(jsonPath("$.recorded").value(true));

        mvc.perform(asAdmin(delete(ADMIN + "/questions/{id}", multipleChoiceId)))
                .andExpect(status().isNoContent());
        mvc.perform(asAdmin(delete(ADMIN + "/questions/{id}", shortAnswerId))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/categories/{slug}/questions", slug))
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
        postJson(post("/api/v1/questions/{id}/answers", multipleChoiceId), "{\"choiceId\": " + choiceId + "}")
                .andExpect(status().isNotFound());
        postJson(post("/api/v1/questions/{id}/text-answers", shortAnswerId), "{\"answer\": \"답\"}")
                .andExpect(status().isNotFound());
        postJson(post("/api/v1/questions/{id}/self-grades", shortAnswerId).cookie(member), "{\"correct\": true}")
                .andExpect(status().isNotFound());

        mvc.perform(asAdmin(get(ADMIN + "/questions/{id}", multipleChoiceId))).andExpect(status().isNotFound());
        mvc.perform(asAdmin(get(ADMIN + "/questions").queryParam("category", slug)))
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
        mvc.perform(asAdmin(delete(ADMIN + "/questions/{id}", multipleChoiceId)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/users/me/attempts").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].questionId", hasItem((int) multipleChoiceId)));
    }

    @Test
    @DisplayName("문제가 남은 카테고리는 409로 삭제할 수 없고, 비운 뒤 삭제하면 공개·관리자 목록에서 빠지며 slug를 다시 쓸 수 없다 (R4, R6, R7, D7)")
    void deletesCategoryOnlyWhenEmpty() throws Exception {
        createCategory(slug).andExpect(status().isCreated());
        long questionId = createMultipleChoice(slug);

        mvc.perform(asAdmin(delete(ADMIN + "/categories/{slug}", slug)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(asAdmin(delete(ADMIN + "/questions/{id}", questionId))).andExpect(status().isNoContent());
        mvc.perform(asAdmin(delete(ADMIN + "/categories/{slug}", slug))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
        mvc.perform(get("/api/v1/categories/{slug}/questions", slug)).andExpect(status().isNotFound());
        mvc.perform(asAdmin(get(ADMIN + "/categories"))).andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
        mvc.perform(asAdmin(get(ADMIN + "/questions").queryParam("category", slug)))
                .andExpect(status().isNotFound());
        mvc.perform(asAdmin(json(put(ADMIN + "/categories/{slug}", slug), "{\"name\": \"다시\", \"sortOrder\": 0}")))
                .andExpect(status().isNotFound());
        mvc.perform(asAdmin(delete(ADMIN + "/categories/{slug}", slug))).andExpect(status().isNotFound());
        mvc.perform(asAdmin(json(post(ADMIN + "/questions"), shortAnswerBody(slug))))
                .andExpect(status().isNotFound());

        createCategory(slug).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("이미 있는 slug로 만들면 409다 (R4)")
    void rejectsDuplicateSlug() throws Exception {
        createCategory(slug).andExpect(status().isCreated());

        createCategory(slug)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("카테고리를 고치면 이름·설명·표시 순서가 바뀌고 slug는 그대로다 (R4)")
    void updatesCategory() throws Exception {
        createCategory(slug).andExpect(status().isCreated());

        mvc.perform(asAdmin(json(
                        put(ADMIN + "/categories/{slug}", slug),
                        "{\"name\": \"바뀐 이름\", \"description\": null, \"sortOrder\": 7}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.name").value("바뀐 이름"))
                .andExpect(jsonPath("$.sortOrder").value(7));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[?(@.slug == '%s')].name".formatted(slug), hasItem("바뀐 이름")));
    }

    @Test
    @DisplayName("정답이 바뀌도록 보기를 통째로 교체하고, 유형도 바꿀 수 있다 (D6)")
    void updatesQuestionReplacingChoices() throws Exception {
        createCategory(slug).andExpect(status().isCreated());
        long questionId = createMultipleChoice(slug);
        long oldChoiceId = firstChoiceId(questionId);

        MvcResult updated = mvc.perform(asAdmin(json(put(ADMIN + "/questions/{id}", questionId), """
                        {"categorySlug": "%s", "type": "MULTIPLE_CHOICE", "content": "바뀐 문제", "explanation": null,
                         "modelAnswer": null, "sortOrder": 5,
                         "choices": [{"content": "새 오답", "correct": false}, {"content": "새 정답", "correct": true},
                                     {"content": "새 오답 2", "correct": false}]}
                        """.formatted(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("바뀐 문제"))
                .andExpect(jsonPath("$.choices.length()").value(3))
                .andExpect(jsonPath("$.choices[*].id", everyItem(notNullValue())))
                .andExpect(jsonPath("$.choices[1].correct").value(true))
                .andReturn();
        List<Integer> newChoiceIds = JsonPath.read(body(updated), "$.choices[*].id");
        assertThat(newChoiceIds).doesNotContain((int) oldChoiceId);

        mvc.perform(asAdmin(json(put(ADMIN + "/questions/{id}", questionId), shortAnswerBody(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$.choices.length()").value(0))
                .andExpect(jsonPath("$.modelAnswer").value("모범 답안"));
        postJson(post("/api/v1/questions/{id}/text-answers", questionId), "{\"answer\": \"답\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAnswer").value("모범 답안"));
    }

    @Test
    @DisplayName("풀이 기록이 생긴 문제는 유형을 바꾸면 409이고 그대로 남으며, 유형이 같으면 고칠 수 있다 (D17)")
    void rejectsTypeChangeAfterAttempt() throws Exception {
        createCategory(slug).andExpect(status().isCreated());
        long questionId = createMultipleChoice(slug);
        long choiceId = firstChoiceId(questionId);
        Cookie member = signUp(uniqueEmail());
        postJson(post("/api/v1/questions/{id}/answers", questionId).cookie(member), "{\"choiceId\": " + choiceId + "}")
                .andExpect(jsonPath("$.recorded").value(true));

        mvc.perform(asAdmin(json(put(ADMIN + "/questions/{id}", questionId), shortAnswerBody(slug))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(asAdmin(get(ADMIN + "/questions/{id}", questionId)))
                .andExpect(jsonPath("$.type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.choices.length()").value(2));

        mvc.perform(asAdmin(json(put(ADMIN + "/questions/{id}", questionId), """
                        {"categorySlug": "%s", "type": "MULTIPLE_CHOICE", "content": "고친 객관식", "explanation": null,
                         "modelAnswer": null, "sortOrder": 10,
                         "choices": [{"content": "새 오답", "correct": false}, {"content": "새 정답", "correct": true}]}
                        """.formatted(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 객관식"));
    }

    @Test
    @DisplayName("답 제출·자기 채점으로 남긴 기록이 통계 API에 회원마다 최근 1건씩 반영되고, 안 푼 문제는 0이다 (통계 R1~R3, D2)")
    void statsReflectRecordedAttempts() throws Exception {
        createCategory(slug).andExpect(status().isCreated());
        long multipleChoiceId = createMultipleChoice(slug);
        long shortAnswerId = createShortAnswer(slug);
        long essayId = createEssay(slug);
        long correctChoiceId = choiceId(multipleChoiceId, 0);
        long wrongChoiceId = choiceId(multipleChoiceId, 1);
        Cookie first = signUp(uniqueEmail());
        Cookie second = signUp(uniqueEmail());

        // 첫 회원은 맞힌 뒤 다시 틀려 최근 기록이 틀림이다
        submitChoice(multipleChoiceId, correctChoiceId, first);
        submitChoice(multipleChoiceId, wrongChoiceId, first);
        submitChoice(multipleChoiceId, correctChoiceId, second);
        selfGrade(shortAnswerId, true, first);
        // 관리자 자신의 기록도 센다 (D2)
        selfGrade(shortAnswerId, false, admin);

        MvcResult categories = mvc.perform(asAdmin(get(ADMIN + "/stats/categories")))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> types =
                JsonPath.read(body(categories), "$[?(@.slug == '%s')].types[*]".formatted(slug));
        assertThat(types)
                .containsExactly(
                        Map.of("type", "MULTIPLE_CHOICE", "solved", 2, "correct", 1),
                        Map.of("type", "SHORT_ANSWER", "solved", 2, "correct", 1));

        mvc.perform(asAdmin(get(ADMIN + "/stats/categories/{slug}/questions", slug)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json("""
                        [
                          {"id": %d, "type": "MULTIPLE_CHOICE", "content": "객관식", "solvedUsers": 2, "correctUsers": 1},
                          {"id": %d, "type": "SHORT_ANSWER", "content": "주관식", "solvedUsers": 2, "correctUsers": 1},
                          {"id": %d, "type": "ESSAY", "content": "서술형", "solvedUsers": 0, "correctUsers": 0}
                        ]
                        """.formatted(multipleChoiceId, shortAnswerId, essayId), JsonCompareMode.STRICT));

        Cookie member = signUp(uniqueEmail());
        mvc.perform(get(ADMIN + "/stats/categories").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/stats/categories/{slug}/questions", slug)).andExpect(status().isUnauthorized());
        mvc.perform(asAdmin(get(ADMIN + "/stats/categories/{slug}/questions", "it-nope-" + slug)))
                .andExpect(status().isNotFound());
    }

    private void submitChoice(long questionId, long choiceId, Cookie member) throws Exception {
        postJson(post("/api/v1/questions/{id}/answers", questionId).cookie(member), "{\"choiceId\": " + choiceId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true));
    }

    private void selfGrade(long questionId, boolean correct, Cookie member) throws Exception {
        postJson(
                        post("/api/v1/questions/{id}/self-grades", questionId).cookie(member),
                        "{\"correct\": " + correct + "}")
                .andExpect(status().isNoContent());
    }

    private long createEssay(String slug) throws Exception {
        MvcResult result = mvc.perform(asAdmin(json(post(ADMIN + "/questions"), """
                        {"categorySlug": "%s", "type": "ESSAY", "content": "서술형", "explanation": null,
                         "modelAnswer": "모범 답안", "sortOrder": 30, "choices": []}
                        """.formatted(slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private long choiceId(long questionId, int index) throws Exception {
        MvcResult result = mvc.perform(asAdmin(get(ADMIN + "/questions/{id}", questionId)))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.choices[%d].id".formatted(index))).longValue();
    }

    private ResultActions createCategory(String slug) throws Exception {
        return mvc.perform(asAdmin(json(
                post(ADMIN + "/categories"),
                "{\"slug\": \"%s\", \"name\": \"통합 테스트\", \"description\": \"설명\", \"sortOrder\": 5000}"
                        .formatted(slug))));
    }

    private long createMultipleChoice(String slug) throws Exception {
        MvcResult result = mvc.perform(asAdmin(json(post(ADMIN + "/questions"), """
                        {"categorySlug": "%s", "type": "MULTIPLE_CHOICE", "content": "객관식", "explanation": "해설",
                         "modelAnswer": null, "sortOrder": 10,
                         "choices": [{"content": "정답", "correct": true}, {"content": "오답", "correct": false}]}
                        """.formatted(slug))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.choices[*].id", everyItem(notNullValue())))
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private long createShortAnswer(String slug) throws Exception {
        MvcResult result = mvc.perform(asAdmin(json(post(ADMIN + "/questions"), shortAnswerBody(slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private static String shortAnswerBody(String slug) {
        return """
                {"categorySlug": "%s", "type": "SHORT_ANSWER", "content": "주관식", "explanation": null,
                 "modelAnswer": "모범 답안", "sortOrder": 20, "choices": []}
                """.formatted(slug);
    }

    private long firstChoiceId(long questionId) throws Exception {
        MvcResult result = mvc.perform(asAdmin(get(ADMIN + "/questions/{id}", questionId)))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(body(result), "$.choices[0].id")).longValue();
    }

    private Cookie signUp(String email) throws Exception {
        MvcResult result = postJson(
                        // 가입 제한 (IP당 10회)에 걸리지 않도록 가입마다 다른 IP로 보낸다 (login-hardening R9)
                        post("/api/v1/users").with(ClientIps.uniqueIp()),
                        "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"관리테스트\"}".formatted(email, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn();
        SetCookieHeader header = SetCookieHeader.from(result);
        return new Cookie(header.name(), header.value());
    }

    private long userId(Cookie cookie) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/users/me").cookie(cookie)).andReturn();
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    /** 운영의 첫 관리자 지정과 같은 방법으로 role을 바꾼다 (범위 밖: DB에서 UPDATE). */
    private void setRole(String email, String role) {
        jdbcTemplate.update("UPDATE users SET role = ? WHERE email = ?", role, email);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(admin);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions postJson(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(json(request, body));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String uniqueEmail() {
        return "admin-it-" + UUID.randomUUID() + "@example.com";
    }
}
