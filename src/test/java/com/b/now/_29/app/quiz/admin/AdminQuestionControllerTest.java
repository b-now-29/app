package com.b.now._29.app.quiz.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.question.InvalidQuestionException;
import com.b.now._29.app.quiz.question.QuestionNotFoundException;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.quiz.question.QuestionTypeChangeNotAllowedException;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관리자 문제 API (R5, R6, R8, API 계약). 구현할 컨트롤러: {@code quiz.admin.AdminQuestionController} ({@code
 * /api/v1/admin/questions}).
 *
 * <p>예외 매핑: {@code InvalidQuestionException} → 400 (detail = 메시지), {@code QuestionNotFoundException}·{@code
 * CategoryNotFoundException} → 404 (기존). 요청 검증: categorySlug·content 공백 불가, type 필수, choices[].content 공백 불가 ({@code
 * @Valid}, D12). 400 문구는 D14 형식이다.
 */
@WebMvcTest(AdminQuestionController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class AdminQuestionControllerTest {

    private static final long ADMIN_ID = 1L;
    private static final String COOKIE_NAME = "access_token";
    private static final String BASE = "/api/v1/admin/questions";
    private static final long QUESTION_ID = 101L;
    private static final String CONTENT = "문자열 내용을 비교할 때 쓰는 것은?";
    private static final String EXPLANATION = "equals()는 문자열 내용을 비교한다.";
    private static final String MODEL_ANSWER = "문자열 내용은 equals()로 비교한다.";

    private static final String MULTIPLE_CHOICE_BODY = """
            {
              "categorySlug": "java", "type": "MULTIPLE_CHOICE",
              "content": "문자열 내용을 비교할 때 쓰는 것은?", "explanation": "equals()는 문자열 내용을 비교한다.",
              "modelAnswer": null, "sortOrder": 10,
              "choices": [{ "content": "==", "correct": false }, { "content": "equals()", "correct": true }]
            }
            """;

    private static final QuestionRequest MULTIPLE_CHOICE_REQUEST = new QuestionRequest(
            "java",
            QuestionType.MULTIPLE_CHOICE,
            CONTENT,
            EXPLANATION,
            null,
            10,
            List.of(new ChoiceRequest("==", false), new ChoiceRequest("equals()", true)));

    private static final AdminQuestionResponse MULTIPLE_CHOICE_RESPONSE = new AdminQuestionResponse(
            QUESTION_ID,
            "java",
            QuestionType.MULTIPLE_CHOICE,
            CONTENT,
            EXPLANATION,
            null,
            10,
            List.of(new AdminChoiceResponse(1001L, "==", false), new AdminChoiceResponse(1002L, "equals()", true)));

    private static final AdminQuestionResponse ESSAY_RESPONSE = new AdminQuestionResponse(
            102L, "java", QuestionType.ESSAY, "equals()와 ==를 비교해 서술하세요.", null, MODEL_ANSWER, 50, List.of());

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleService userRoleService;

    @MockitoBean
    AdminQuestionService adminQuestionService;

    @BeforeEach
    void setUp() {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);
    }

    @Test
    @DisplayName("카테고리의 문제 목록을 보기의 정답 여부와 모범 답안까지 포함해 반환한다 (R8)")
    void getQuestions() throws Exception {
        given(adminQuestionService.getQuestions("java")).willReturn(List.of(MULTIPLE_CHOICE_RESPONSE, ESSAY_RESPONSE));

        mvc.perform(asAdmin(get(BASE).queryParam("category", "java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(
                        "$[0].keys()",
                        containsInAnyOrder(
                                "id",
                                "categorySlug",
                                "type",
                                "content",
                                "explanation",
                                "modelAnswer",
                                "sortOrder",
                                "choices")))
                .andExpect(jsonPath("$[0].choices[1].keys()", containsInAnyOrder("id", "content", "correct")))
                .andExpect(jsonPath("$[0].choices[1].correct").value(true))
                .andExpect(jsonPath("$[1].modelAnswer").value(MODEL_ANSWER))
                .andDo(document(
                        "admin-questions-list",
                        requestCookies(cookieWithName(COOKIE_NAME).description("ADMIN 회원의 인증 쿠키")),
                        queryParameters(parameterWithName("category").description("카테고리 식별자 (slug)")),
                        responseFields(questionFields("[]."))));
    }

    @Test
    @DisplayName("category 파라미터가 없으면 400 ProblemDetail이고 Service를 호출하지 않는다")
    void getQuestionsWithoutCategory() throws Exception {
        mvc.perform(asAdmin(get(BASE))).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리의 문제 목록은 404 ProblemDetail이다")
    void getQuestionsOfUnknownCategory() throws Exception {
        given(adminQuestionService.getQuestions("nope")).willThrow(new CategoryNotFoundException("nope"));

        mvc.perform(asAdmin(get(BASE).queryParam("category", "nope"))).andExpect(problemDetail(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("문제 하나를 정답 정보까지 포함해 반환한다 (R8)")
    void getQuestion() throws Exception {
        given(adminQuestionService.getQuestion(QUESTION_ID)).willReturn(MULTIPLE_CHOICE_RESPONSE);

        mvc.perform(asAdmin(get(BASE + "/{id}", QUESTION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(QUESTION_ID))
                .andExpect(jsonPath("$.modelAnswer").value(nullValue()))
                .andExpect(jsonPath("$.choices[0].correct").value(false))
                .andDo(document(
                        "admin-questions-get",
                        pathParameters(parameterWithName("id").description("문제 ID")),
                        responseFields(questionFields(""))));
    }

    @Test
    @DisplayName("없거나 삭제한 문제는 404 ProblemDetail이다 (R6)")
    void getUnknownQuestion() throws Exception {
        QuestionNotFoundException exception = new QuestionNotFoundException(999L);
        given(adminQuestionService.getQuestion(999L)).willThrow(exception);

        mvc.perform(asAdmin(get(BASE + "/{id}", 999L)))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-questions-get-not-found"));
    }

    @Test
    @DisplayName("경로의 id가 숫자가 아니면 400이고 Service를 호출하지 않는다")
    void rejectsNonNumericId() throws Exception {
        mvc.perform(asAdmin(get(BASE + "/{id}", "abc"))).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("문제를 등록하면 201과 id, 보기 id를 포함한 문제를 응답한다 (R5, API 계약)")
    void createQuestion() throws Exception {
        given(adminQuestionService.create(MULTIPLE_CHOICE_REQUEST)).willReturn(MULTIPLE_CHOICE_RESPONSE);

        mvc.perform(asAdmin(json(post(BASE), MULTIPLE_CHOICE_BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(QUESTION_ID))
                .andExpect(jsonPath("$.categorySlug").value("java"))
                .andExpect(jsonPath("$.choices[0].id").value(1001L))
                .andExpect(jsonPath("$.choices[1].correct").value(true))
                .andDo(document(
                        "admin-questions-create",
                        requestFields(
                                fieldWithPath("categorySlug").description("카테고리 식별자 (삭제하지 않은 카테고리)"),
                                fieldWithPath("type").description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("content").description("문제 내용"),
                                fieldWithPath("explanation").optional().description("해설 (없으면 null)"),
                                fieldWithPath("modelAnswer")
                                        .optional()
                                        .description("모범 답안. 주관식·서술형은 필수 (공백만은 안 됨), 객관식은 null"),
                                fieldWithPath("sortOrder").description("표시 순서"),
                                fieldWithPath("choices").description("보기 목록. 객관식은 2개 이상에 정답 정확히 1개, 주관식·서술형은 빈 배열"),
                                fieldWithPath("choices[].content").description("보기 내용 (공백만은 안 됨)"),
                                fieldWithPath("choices[].correct").description("정답 여부")),
                        responseFields(questionFields(""))));

        then(adminQuestionService).should().create(MULTIPLE_CHOICE_REQUEST);
    }

    @Test
    @DisplayName("서술형은 모범 답안과 빈 보기 목록으로 등록한다 (R5)")
    void createEssay() throws Exception {
        QuestionRequest request = new QuestionRequest(
                "java", QuestionType.ESSAY, "equals()와 ==를 비교해 서술하세요.", null, MODEL_ANSWER, 50, List.of());
        given(adminQuestionService.create(request)).willReturn(ESSAY_RESPONSE);

        mvc.perform(asAdmin(json(post(BASE), """
                        {
                          "categorySlug": "java", "type": "ESSAY", "content": "equals()와 ==를 비교해 서술하세요.",
                          "explanation": null, "modelAnswer": "문자열 내용은 equals()로 비교한다.", "sortOrder": 50,
                          "choices": []
                        }
                        """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modelAnswer").value(MODEL_ANSWER))
                .andExpect(jsonPath("$.choices.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"categorySlug\": \"java\", \"content\": \"문제\", \"sortOrder\": 0, \"choices\": []}",
                "{\"categorySlug\": \"java\", \"type\": \"TRUE_FALSE\", \"content\": \"문제\", \"sortOrder\": 0, \"choices\": []}",
                "{\"categorySlug\": \"java\", \"type\": \"ESSAY\", \"content\": \"  \", \"modelAnswer\": \"답\", \"sortOrder\": 0, \"choices\": []}",
                "{\"categorySlug\": \" \", \"type\": \"ESSAY\", \"content\": \"문제\", \"modelAnswer\": \"답\", \"sortOrder\": 0, \"choices\": []}",
                "{\"categorySlug\": \"java\", \"type\": \"MULTIPLE_CHOICE\", \"content\": \"문제\", \"sortOrder\": 0, \"choices\": [{\"content\": \" \", \"correct\": true}, {\"content\": \"B\", \"correct\": false}]}",
                "{\"categorySlug\": ",
                ""
            })
    @DisplayName("type 누락·알 수 없는 유형, 공백 내용·카테고리·보기, 깨진 JSON, 빈 본문은 400이고 Service를 호출하지 않는다")
    void rejectsInvalidBody(String body) throws Exception {
        mvc.perform(asAdmin(json(post(BASE), body))).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("보기 내용이 공백이면 400이고 detail은 그 보기의 경로를 알려 준다 (D12, D14)")
    void rejectsBlankChoiceContent() throws Exception {
        mvc.perform(asAdmin(json(post(BASE), """
                        {"categorySlug": "java", "type": "MULTIPLE_CHOICE", "content": "문제", "sortOrder": 0,
                         "choices": [{"content": "A", "correct": true}, {"content": "  ", "correct": false}]}
                        """)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: choices[1].content)"));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("여러 항목이 실패하면 detail에 필드 경로를 정렬해 담는다 (D14)")
    void reportsFailedFieldsInOrder() throws Exception {
        mvc.perform(asAdmin(json(post(BASE), """
                        {"categorySlug": " ", "type": "MULTIPLE_CHOICE", "content": " ", "sortOrder": 0,
                         "choices": [{"content": " ", "correct": true}, {"content": "B", "correct": false}]}
                        """)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(
                        jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: categorySlug, choices[0].content, content)"));
    }

    @Test
    @DisplayName("없는 유형 값은 400이고 detail은 요청 형식 문구다 (D14)")
    void rejectsUnknownType() throws Exception {
        mvc.perform(asAdmin(json(post(BASE), """
                        {"categorySlug": "java", "type": "TRUE_FALSE", "content": "문제", "sortOrder": 0, "choices": []}
                        """)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("요청 형식이 올바르지 않습니다."));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유형별 규칙을 어기면 400 ProblemDetail이고 detail에 이유를 담는다 (R5)")
    void rejectsQuestionBreakingTypeRule() throws Exception {
        InvalidQuestionException exception = new InvalidQuestionException("객관식은 정답 보기가 정확히 1개여야 합니다.");
        given(adminQuestionService.create(MULTIPLE_CHOICE_REQUEST)).willThrow(exception);

        mvc.perform(asAdmin(json(post(BASE), MULTIPLE_CHOICE_BODY)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-questions-create-invalid"));
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리에 등록하면 404 ProblemDetail이다")
    void createInUnknownCategory() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("java");
        given(adminQuestionService.create(MULTIPLE_CHOICE_REQUEST)).willThrow(exception);

        mvc.perform(asAdmin(json(post(BASE), MULTIPLE_CHOICE_BODY)))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-questions-create-category-not-found"));
    }

    @Test
    @DisplayName("문제를 고치면 200과 고친 문제를 응답한다. 보기는 통째로 바뀌어 새 id를 갖는다 (D6)")
    void updateQuestion() throws Exception {
        given(adminQuestionService.update(QUESTION_ID, MULTIPLE_CHOICE_REQUEST)).willReturn(MULTIPLE_CHOICE_RESPONSE);

        mvc.perform(asAdmin(json(put(BASE + "/{id}", QUESTION_ID), MULTIPLE_CHOICE_BODY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(QUESTION_ID))
                .andDo(document(
                        "admin-questions-update",
                        pathParameters(parameterWithName("id").description("고칠 문제 ID")),
                        responseFields(questionFields(""))));

        then(adminQuestionService).should().update(QUESTION_ID, MULTIPLE_CHOICE_REQUEST);
    }

    @Test
    @DisplayName("없거나 삭제한 문제를 고치면 404 ProblemDetail이다")
    void updateUnknownQuestion() throws Exception {
        given(adminQuestionService.update(999L, MULTIPLE_CHOICE_REQUEST))
                .willThrow(new QuestionNotFoundException(999L));

        mvc.perform(asAdmin(json(put(BASE + "/{id}", 999L), MULTIPLE_CHOICE_BODY)))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andDo(document("admin-questions-update-not-found"));
    }

    @Test
    @DisplayName("풀이 기록이 있는 문제의 유형을 바꾸면 409 ProblemDetail이다 (D17)")
    void rejectsTypeChangeOfAttemptedQuestion() throws Exception {
        QuestionTypeChangeNotAllowedException exception =
                new QuestionTypeChangeNotAllowedException(QuestionType.MULTIPLE_CHOICE, QuestionType.ESSAY);
        given(adminQuestionService.update(QUESTION_ID, MULTIPLE_CHOICE_REQUEST)).willThrow(exception);

        mvc.perform(asAdmin(json(put(BASE + "/{id}", QUESTION_ID), MULTIPLE_CHOICE_BODY)))
                .andExpect(problemDetail(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value("풀이 기록이 있는 문제는 유형을 바꿀 수 없습니다."))
                .andDo(document("admin-questions-update-type-locked"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[null]", "[{\"content\": \"A\", \"correct\": true}, null]"})
    @DisplayName("choices에 null 원소가 있으면 500이 아니라 400이고 detail은 그 원소 경로를 알려 준다 (D18, D14)")
    void rejectsNullChoice(String choices) throws Exception {
        String index = choices.startsWith("[null") ? "0" : "1";

        mvc.perform(asAdmin(json(post(BASE), """
                        {"categorySlug": "java", "type": "MULTIPLE_CHOICE", "content": "문제", "sortOrder": 0,
                         "choices": %s}
                        """.formatted(choices))))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: choices[" + index + "])"));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("수정 요청의 choices에 null 원소가 있어도 400이다 (D18)")
    void rejectsNullChoiceOnUpdate() throws Exception {
        mvc.perform(asAdmin(json(put(BASE + "/{id}", QUESTION_ID), """
                        {"categorySlug": "java", "type": "MULTIPLE_CHOICE", "content": "문제", "sortOrder": 0,
                         "choices": [null]}
                        """)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: choices[0])"));

        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("규칙을 어긴 수정은 400 ProblemDetail이다 (R5)")
    void rejectsInvalidUpdate() throws Exception {
        given(adminQuestionService.update(QUESTION_ID, MULTIPLE_CHOICE_REQUEST))
                .willThrow(new InvalidQuestionException("객관식은 보기가 2개 이상이어야 합니다."));

        mvc.perform(asAdmin(json(put(BASE + "/{id}", QUESTION_ID), MULTIPLE_CHOICE_BODY)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("객관식은 보기가 2개 이상이어야 합니다."));
    }

    @Test
    @DisplayName("문제를 삭제하면 204 본문 없음이다 (R6)")
    void deleteQuestion() throws Exception {
        mvc.perform(asAdmin(delete(BASE + "/{id}", QUESTION_ID)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andDo(document(
                        "admin-questions-delete",
                        pathParameters(parameterWithName("id").description("삭제할 문제 ID"))));

        then(adminQuestionService).should().delete(QUESTION_ID);
    }

    @Test
    @DisplayName("없거나 이미 삭제한 문제를 삭제하면 404 ProblemDetail이다")
    void deleteUnknownQuestion() throws Exception {
        willThrow(new QuestionNotFoundException(999L))
                .given(adminQuestionService)
                .delete(999L);

        mvc.perform(asAdmin(delete(BASE + "/{id}", 999L)))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andDo(document("admin-questions-delete-not-found"));
    }

    private static FieldDescriptor[] questionFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("문제 ID"),
            fieldWithPath(prefix + "categorySlug").description("카테고리 식별자"),
            fieldWithPath(prefix + "type").description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
            fieldWithPath(prefix + "content").description("문제 내용"),
            fieldWithPath(prefix + "explanation").optional().description("해설 (없으면 null)"),
            fieldWithPath(prefix + "modelAnswer").optional().description("모범 답안 (객관식은 null)"),
            fieldWithPath(prefix + "sortOrder").description("표시 순서"),
            fieldWithPath(prefix + "choices").description("보기 목록 (주관식·서술형은 빈 배열)"),
            fieldWithPath(prefix + "choices[].id").optional().description("보기 ID (수정하면 새 ID가 된다)"),
            fieldWithPath(prefix + "choices[].content").optional().description("보기 내용"),
            fieldWithPath(prefix + "choices[].correct").optional().description("정답 여부")
        };
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(COOKIE_NAME, TestJwts.valid(ADMIN_ID)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
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
