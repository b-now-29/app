package com.b.now._29.app.quiz.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.UserRoleService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관리자 통계 API (docs/features/admin-stats.md R2, R3, R5, R6, API 계약). 구현할 컨트롤러: {@code
 * quiz.admin.AdminStatsController} ({@code @RestController}, {@code /api/v1/admin/stats}).
 *
 * <ul>
 *   <li>{@code GET /categories} → {@code adminStatsService.getCategoryStats()} 그대로 200
 *   <li>{@code GET /categories/{slug}/questions} → {@code adminStatsService.getQuestionStats(slug)} 그대로 200. {@code
 *       CategoryNotFoundException} → 404 (기존 advice)
 *   <li>DTO는 {@code AdminStatsRepositoryTest} 맨 위 주석 참고
 * </ul>
 */
@WebMvcTest(AdminStatsController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class AdminStatsControllerTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;
    private static final String COOKIE_NAME = "access_token";
    private static final String BASE = "/api/v1/admin/stats";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleService userRoleService;

    @MockitoBean
    AdminStatsService adminStatsService;

    @BeforeEach
    void setUp() {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);
    }

    @Test
    @DisplayName("카테고리 통계는 카테고리마다 유형별 solved·correct를 주고, 기록이 없는 카테고리는 types가 빈 배열이다 (R2, API 계약)")
    void getCategoryStats() throws Exception {
        given(adminStatsService.getCategoryStats())
                .willReturn(List.of(
                        new AdminCategoryStatResponse(
                                "java",
                                "Java",
                                List.of(
                                        new AdminTypeStatResponse(QuestionType.MULTIPLE_CHOICE, 12, 9),
                                        new AdminTypeStatResponse(QuestionType.ESSAY, 3, 1))),
                        new AdminCategoryStatResponse("spring", "Spring", List.of())));

        mvc.perform(asAdmin(get(BASE + "/categories")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"slug": "java", "name": "Java", "types": [
                            {"type": "MULTIPLE_CHOICE", "solved": 12, "correct": 9},
                            {"type": "ESSAY", "solved": 3, "correct": 1}]},
                          {"slug": "spring", "name": "Spring", "types": []}
                        ]
                        """, JsonCompareMode.STRICT))
                .andDo(document(
                        "admin-stats-categories",
                        requestCookies(cookieWithName(COOKIE_NAME).description("ADMIN 회원의 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].slug").description("카테고리 식별자"),
                                fieldWithPath("[].name").description("카테고리 이름"),
                                fieldWithPath("[].types")
                                        .description(
                                                "유형별 집계. 기록이 있는 유형만 MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY 순서. 기록이 없으면 빈 배열"),
                                fieldWithPath("[].types[].type")
                                        .description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("[].types[].solved")
                                        .description("푼 회원·문제 쌍의 수. 회원마다 문제당 가장 최근 기록 1건만 센다"),
                                fieldWithPath("[].types[].correct").description("그중 최근 기록이 정답인 쌍의 수"))));
    }

    @Test
    @DisplayName("문제 통계는 문제마다 id·유형·내용·푼 회원 수·정답 회원 수를 주고 정답 정보는 없다 (R3, R6, API 계약)")
    void getQuestionStats() throws Exception {
        given(adminStatsService.getQuestionStats("java"))
                .willReturn(List.of(
                        new AdminQuestionStatResponse(1L, QuestionType.MULTIPLE_CHOICE, "문자열 비교는?", 5, 4),
                        new AdminQuestionStatResponse(2L, QuestionType.ESSAY, "equals()를 설명하세요.", 0, 0)));

        mvc.perform(asAdmin(get(BASE + "/categories/{slug}/questions", "java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[0].keys()", containsInAnyOrder("id", "type", "content", "solvedUsers", "correctUsers")))
                .andExpect(content().json("""
                        [
                          {"id": 1, "type": "MULTIPLE_CHOICE", "content": "문자열 비교는?", "solvedUsers": 5, "correctUsers": 4},
                          {"id": 2, "type": "ESSAY", "content": "equals()를 설명하세요.", "solvedUsers": 0, "correctUsers": 0}
                        ]
                        """, JsonCompareMode.STRICT))
                .andDo(document(
                        "admin-stats-questions",
                        requestCookies(cookieWithName(COOKIE_NAME).description("ADMIN 회원의 인증 쿠키")),
                        pathParameters(parameterWithName("slug").description("카테고리 식별자")),
                        responseFields(
                                fieldWithPath("[].id").description("문제 ID"),
                                fieldWithPath("[].type").description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("[].content").description("문제 내용"),
                                fieldWithPath("[].solvedUsers").description("이 문제를 푼 회원 수. 회원마다 가장 최근 기록 1건만 센다"),
                                fieldWithPath("[].correctUsers").description("그중 최근 기록이 정답인 회원 수"))));

        then(adminStatsService).should().getQuestionStats("java");
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리의 문제 통계는 404 ProblemDetail이다 (API 계약)")
    void questionStatsOfUnknownCategory() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("nope");
        given(adminStatsService.getQuestionStats("nope")).willThrow(exception);

        mvc.perform(asAdmin(get(BASE + "/categories/{slug}/questions", "nope")))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-stats-questions-not-found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categories", "/categories/java/questions"})
    @DisplayName("비회원은 401 ProblemDetail이고 Service를 호출하지 않는다 (R5)")
    void rejectsGuest(String path) throws Exception {
        mvc.perform(get(BASE + path)).andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(adminStatsService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categories", "/categories/java/questions"})
    @DisplayName("USER 회원은 403 ProblemDetail이고 Service를 호출하지 않는다 (R5)")
    void rejectsUser(String path) throws Exception {
        mvc.perform(get(BASE + path).cookie(new Cookie(COOKIE_NAME, TestJwts.valid(USER_ID))))
                .andExpect(problemDetail(HttpStatus.FORBIDDEN));

        then(adminStatsService).shouldHaveNoInteractions();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(COOKIE_NAME, TestJwts.valid(ADMIN_ID)));
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
