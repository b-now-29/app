package com.b.now._29.app.quiz.wronganswer;

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
import com.b.now._29.app.quiz.question.ChoiceResponse;
import com.b.now._29.app.quiz.question.QuestionResponse;
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

@WebMvcTest(WrongAnswerController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class WrongAnswerControllerTest {

    private static final long USER_ID = 7L;
    private static final String COOKIE_NAME = "access_token";
    private static final String SLUG = "java";
    private static final String WRONG_ANSWERS = "/api/v1/users/me/wrong-answers";
    private static final String WRONG_QUESTIONS = "/api/v1/users/me/wrong-answers/{slug}/questions";
    private static final String WRONG_QUESTIONS_OF_SLUG = "/api/v1/users/me/wrong-answers/java/questions";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WrongAnswerService wrongAnswerService;

    // 오답 목록

    @Test
    @DisplayName("오답 목록은 카테고리별 slug, name, questions만 내려주고 정답 정보, 모범 답안, 해설은 내려주지 않는다 (R5, R6)")
    void getWrongAnswers() throws Exception {
        given(wrongAnswerService.getWrongAnswers(USER_ID))
                .willReturn(List.of(
                        new WrongAnswerCategoryResponse(
                                "java",
                                "자바",
                                List.of(
                                        new WrongAnswerQuestionResponse(
                                                3L, "문제 본문", QuestionType.ESSAY, Instant.parse("2026-11-06T01:23:45Z")),
                                        new WrongAnswerQuestionResponse(
                                                1L,
                                                "객관식 본문",
                                                QuestionType.MULTIPLE_CHOICE,
                                                Instant.parse("2026-11-06T01:20:00Z")))),
                        new WrongAnswerCategoryResponse(
                                "spring",
                                "스프링",
                                List.of(new WrongAnswerQuestionResponse(
                                        5L,
                                        "주관식 본문",
                                        QuestionType.SHORT_ANSWER,
                                        Instant.parse("2026-11-05T10:00:00Z"))))));

        mvc.perform(get(WRONG_ANSWERS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].keys()", containsInAnyOrder("slug", "name", "questions")))
                .andExpect(jsonPath("$[0].slug").value("java"))
                .andExpect(jsonPath("$[0].name").value("자바"))
                .andExpect(jsonPath("$[0].questions.length()").value(2))
                .andExpect(jsonPath(
                        "$[0].questions[0].keys()", containsInAnyOrder("questionId", "content", "type", "attemptedAt")))
                .andExpect(jsonPath("$[0].questions[0].questionId").value(3))
                .andExpect(jsonPath("$[0].questions[0].content").value("문제 본문"))
                .andExpect(jsonPath("$[0].questions[0].type").value("ESSAY"))
                .andExpect(jsonPath("$[0].questions[0].attemptedAt").value("2026-11-06T01:23:45Z"))
                .andExpect(jsonPath("$[0].questions[1].questionId").value(1))
                .andExpect(jsonPath("$[1].slug").value("spring"))
                .andExpect(jsonPath("$[1].questions[0].type").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$..correctChoiceId").isEmpty())
                .andExpect(jsonPath("$..modelAnswer").isEmpty())
                .andExpect(jsonPath("$..explanation").isEmpty())
                .andExpect(jsonPath("$..is_correct").isEmpty())
                .andExpect(jsonPath("$..correct").isEmpty())
                .andDo(document(
                        "users-me-wrong-answers",
                        requestCookies(cookieWithName(COOKIE_NAME).description("로그인할 때 받은 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].slug").description("카테고리 식별자"),
                                fieldWithPath("[].name").description("카테고리 이름"),
                                fieldWithPath("[].questions").description("오답 문제 목록 (마지막으로 푼 시각 최신순)"),
                                fieldWithPath("[].questions[].questionId").description("문제 ID"),
                                fieldWithPath("[].questions[].content").description("문제 내용"),
                                fieldWithPath("[].questions[].type")
                                        .description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("[].questions[].attemptedAt")
                                        .description("마지막으로 푼 시각 (ISO-8601, UTC)"))));

        then(wrongAnswerService).should().getWrongAnswers(USER_ID);
    }

    @Test
    @DisplayName("오답이 없으면 오답 목록은 빈 배열이다")
    void getWrongAnswersReturnsEmptyArray() throws Exception {
        given(wrongAnswerService.getWrongAnswers(USER_ID)).willReturn(List.of());

        mvc.perform(get(WRONG_ANSWERS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("비회원이 오답 목록을 조회하면 401 ProblemDetail이고 Service를 호출하지 않는다 (R4)")
    void getWrongAnswersWithoutLogin() throws Exception {
        mvc.perform(get(WRONG_ANSWERS))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-wrong-answers-unauthorized"));

        then(wrongAnswerService).shouldHaveNoInteractions();
    }

    // 다시 풀기용 문제

    @Test
    @DisplayName("다시 풀기용 문제는 기존 문제 API와 같은 id, type, content, choices만 내려주고 정답 정보는 내려주지 않는다 (R5, D4)")
    void getWrongQuestions() throws Exception {
        given(wrongAnswerService.getWrongQuestions(USER_ID, SLUG))
                .willReturn(List.of(
                        new QuestionResponse(
                                1L,
                                QuestionType.MULTIPLE_CHOICE,
                                "문자열 내용을 비교할 때 쓰는 것은?",
                                List.of(new ChoiceResponse(10L, "=="), new ChoiceResponse(11L, "equals()"))),
                        new QuestionResponse(3L, QuestionType.ESSAY, "equals()와 ==를 비교해 서술하세요.", List.of())));

        mvc.perform(get(WRONG_QUESTIONS, SLUG).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].keys()", containsInAnyOrder("id", "type", "content", "choices")))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$[0].choices.length()").value(2))
                .andExpect(jsonPath("$[0].choices[0].keys()", containsInAnyOrder("id", "content")))
                .andExpect(jsonPath("$[0].choices[1].id").value(11))
                .andExpect(jsonPath("$[1].keys()", containsInAnyOrder("id", "type", "content", "choices")))
                .andExpect(jsonPath("$[1].id").value(3))
                .andExpect(jsonPath("$[1].type").value("ESSAY"))
                .andExpect(jsonPath("$[1].choices.length()").value(0))
                .andExpect(jsonPath("$..correctChoiceId").isEmpty())
                .andExpect(jsonPath("$..modelAnswer").isEmpty())
                .andExpect(jsonPath("$..explanation").isEmpty())
                .andExpect(jsonPath("$..is_correct").isEmpty())
                .andExpect(jsonPath("$..correct").isEmpty())
                .andDo(document(
                        "users-me-wrong-answers-questions",
                        pathParameters(parameterWithName("slug").description("카테고리 식별자")),
                        requestCookies(cookieWithName(COOKIE_NAME).description("로그인할 때 받은 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].id").description("문제 ID"),
                                fieldWithPath("[].type").description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("[].content").description("문제 내용"),
                                fieldWithPath("[].choices").description("보기 목록 (주관식과 서술형은 빈 배열)"),
                                fieldWithPath("[].choices[].id").description("보기 ID"),
                                fieldWithPath("[].choices[].content").description("보기 내용"))));

        then(wrongAnswerService).should().getWrongQuestions(USER_ID, SLUG);
    }

    @Test
    @DisplayName("그 카테고리에 오답이 없으면 다시 풀기용 문제는 빈 배열이다")
    void getWrongQuestionsReturnsEmptyArray() throws Exception {
        given(wrongAnswerService.getWrongQuestions(USER_ID, SLUG)).willReturn(List.of());

        mvc.perform(get(WRONG_QUESTIONS, SLUG).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("없거나 삭제된 카테고리의 다시 풀기용 문제는 404 ProblemDetail이다")
    void getWrongQuestionsOfUnknownCategory() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("nope");
        given(wrongAnswerService.getWrongQuestions(USER_ID, "nope")).willThrow(exception);

        mvc.perform(get(WRONG_QUESTIONS, "nope").cookie(validCookie()))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("users-me-wrong-answers-questions-category-not-found"));
    }

    @Test
    @DisplayName("비회원이 다시 풀기용 문제를 조회하면 401 ProblemDetail이고 Service를 호출하지 않는다 (R4)")
    void getWrongQuestionsWithoutLogin() throws Exception {
        mvc.perform(get(WRONG_QUESTIONS, SLUG))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-wrong-answers-questions-unauthorized"));

        then(wrongAnswerService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {WRONG_ANSWERS, WRONG_QUESTIONS_OF_SLUG})
    @DisplayName("토큰이 만료되면 오답 조회는 401 ProblemDetail이고 Service를 호출하지 않는다 (R4)")
    void rejectsExpiredToken(String path) throws Exception {
        mvc.perform(get(path).cookie(new Cookie(COOKIE_NAME, TestJwts.expired(USER_ID))))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(wrongAnswerService).shouldHaveNoInteractions();
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
