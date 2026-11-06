package com.b.now._29.app.quiz.attempt;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(QuestionAttemptController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class QuestionAttemptControllerTest {

    private static final long USER_ID = 7L;
    private static final String COOKIE_NAME = "access_token";
    private static final String CATEGORY_STATS = "/api/v1/users/me/category-stats";
    private static final String ATTEMPTS = "/api/v1/users/me/attempts";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QuestionAttemptService questionAttemptService;

    // 카테고리별 정답률

    @Test
    @DisplayName("카테고리별 정답률은 slug, name, types만 내려주고 types는 유형별 푼 문제 수와 맞힌 문제 수다 (R6)")
    void getCategoryStats() throws Exception {
        given(questionAttemptService.getCategoryStats(USER_ID))
                .willReturn(List.of(
                        new CategoryStatResponse(
                                "java",
                                "자바",
                                List.of(
                                        new TypeStatResponse(QuestionType.MULTIPLE_CHOICE, 6, 5),
                                        new TypeStatResponse(QuestionType.ESSAY, 4, 2))),
                        new CategoryStatResponse(
                                "spring", "스프링", List.of(new TypeStatResponse(QuestionType.SHORT_ANSWER, 1, 0)))));

        mvc.perform(get(CATEGORY_STATS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].keys()", containsInAnyOrder("slug", "name", "types")))
                .andExpect(jsonPath("$[0].slug").value("java"))
                .andExpect(jsonPath("$[0].name").value("자바"))
                .andExpect(jsonPath("$[0].types.length()").value(2))
                .andExpect(jsonPath(
                        "$[0].types[0].keys()", containsInAnyOrder("type", "solvedQuestions", "correctQuestions")))
                .andExpect(jsonPath("$[0].types[0].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$[0].types[0].solvedQuestions").value(6))
                .andExpect(jsonPath("$[0].types[0].correctQuestions").value(5))
                .andExpect(jsonPath("$[0].types[1].type").value("ESSAY"))
                .andExpect(jsonPath("$[0].types[1].solvedQuestions").value(4))
                .andExpect(jsonPath("$[0].types[1].correctQuestions").value(2))
                .andExpect(jsonPath("$[1].slug").value("spring"))
                .andExpect(jsonPath("$[1].types[0].type").value("SHORT_ANSWER"))
                .andDo(document(
                        "users-me-category-stats",
                        requestCookies(cookieWithName(COOKIE_NAME).description("로그인할 때 받은 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].slug").description("카테고리 식별자"),
                                fieldWithPath("[].name").description("카테고리 이름"),
                                fieldWithPath("[].types")
                                        .description("기록이 있는 유형별 집계 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY 순서)"),
                                fieldWithPath("[].types[].type").description("문제 유형"),
                                fieldWithPath("[].types[].solvedQuestions").description("푼 문제 수 (문제마다 가장 최근 기록 기준)"),
                                fieldWithPath("[].types[].correctQuestions").description("가장 최근 기록이 맞음인 문제 수"))));

        then(questionAttemptService).should().getCategoryStats(USER_ID);
    }

    @Test
    @DisplayName("기록이 없으면 카테고리별 정답률은 빈 배열이다")
    void getCategoryStatsReturnsEmptyArray() throws Exception {
        given(questionAttemptService.getCategoryStats(USER_ID)).willReturn(List.of());

        mvc.perform(get(CATEGORY_STATS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("비회원이 카테고리별 정답률을 조회하면 401 ProblemDetail이고 Service를 호출하지 않는다 (R8)")
    void getCategoryStatsWithoutLogin() throws Exception {
        mvc.perform(get(CATEGORY_STATS))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-category-stats-unauthorized"));

        then(questionAttemptService).shouldHaveNoInteractions();
    }

    // 최근 풀이 목록

    @Test
    @DisplayName("최근 풀이 목록은 정해진 필드만 내려주고 정답 보기, 모범 답안, 해설은 내려주지 않는다 (R7, R9)")
    void getRecentAttempts() throws Exception {
        given(questionAttemptService.getRecentAttempts(USER_ID))
                .willReturn(List.of(
                        new AttemptResponse(
                                42L,
                                3L,
                                "문제 본문",
                                QuestionType.ESSAY,
                                "자바",
                                false,
                                Instant.parse("2026-11-06T01:23:45Z")),
                        new AttemptResponse(
                                41L,
                                1L,
                                "객관식 본문",
                                QuestionType.MULTIPLE_CHOICE,
                                "자바",
                                true,
                                Instant.parse("2026-11-06T01:20:00Z"))));

        mvc.perform(get(ATTEMPTS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(
                        "$[0].keys()",
                        containsInAnyOrder(
                                "id",
                                "questionId",
                                "questionContent",
                                "questionType",
                                "categoryName",
                                "correct",
                                "attemptedAt")))
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].questionId").value(3))
                .andExpect(jsonPath("$[0].questionContent").value("문제 본문"))
                .andExpect(jsonPath("$[0].questionType").value("ESSAY"))
                .andExpect(jsonPath("$[0].categoryName").value("자바"))
                .andExpect(jsonPath("$[0].correct").value(false))
                .andExpect(jsonPath("$[0].attemptedAt").value("2026-11-06T01:23:45Z"))
                .andExpect(jsonPath("$[1].id").value(41))
                .andExpect(jsonPath("$[*].correctChoiceId").doesNotExist())
                .andExpect(jsonPath("$[*].modelAnswer").doesNotExist())
                .andExpect(jsonPath("$[*].explanation").doesNotExist())
                .andDo(document(
                        "users-me-attempts",
                        requestCookies(cookieWithName(COOKIE_NAME).description("로그인할 때 받은 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].id").description("풀이 기록 ID"),
                                fieldWithPath("[].questionId").description("문제 ID"),
                                fieldWithPath("[].questionContent").description("문제 내용"),
                                fieldWithPath("[].questionType")
                                        .description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("[].categoryName").description("카테고리 이름"),
                                fieldWithPath("[].correct").description("맞음 여부"),
                                fieldWithPath("[].attemptedAt").description("푼 시각 (ISO-8601, UTC)"))));

        then(questionAttemptService).should().getRecentAttempts(USER_ID);
    }

    @Test
    @DisplayName("기록이 없으면 최근 풀이 목록은 빈 배열이다")
    void getRecentAttemptsReturnsEmptyArray() throws Exception {
        given(questionAttemptService.getRecentAttempts(USER_ID)).willReturn(List.of());

        mvc.perform(get(ATTEMPTS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("비회원이 최근 풀이 목록을 조회하면 401 ProblemDetail이고 Service를 호출하지 않는다 (R8)")
    void getRecentAttemptsWithoutLogin() throws Exception {
        mvc.perform(get(ATTEMPTS))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-attempts-unauthorized"));

        then(questionAttemptService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {CATEGORY_STATS, ATTEMPTS})
    @DisplayName("토큰이 만료되면 기록 조회는 401 ProblemDetail이고 Service를 호출하지 않는다 (R8)")
    void rejectsExpiredToken(String path) throws Exception {
        mvc.perform(get(path).cookie(new Cookie(COOKIE_NAME, TestJwts.expired(USER_ID))))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(questionAttemptService).shouldHaveNoInteractions();
    }

    private static Cookie validCookie() {
        return new Cookie(COOKIE_NAME, TestJwts.valid(USER_ID));
    }

    private static ResultMatcher problemDetail(HttpStatus expected) {
        return result -> {
            status().is(expected.value()).match(result);
            content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(expected.value()).match(result);
            jsonPath("$.title").exists().match(result);
        };
    }
}
