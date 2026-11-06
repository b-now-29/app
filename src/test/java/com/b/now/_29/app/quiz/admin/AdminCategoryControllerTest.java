package com.b.now._29.app.quiz.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.category.CategoryNotEmptyException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.DuplicateCategorySlugException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관리자 카테고리 API (R4, R7, API 계약). 구현할 컨트롤러: {@code quiz.admin.AdminCategoryController} ({@code
 * /api/v1/admin/categories}).
 *
 * <p>예외 매핑: {@code DuplicateCategorySlugException}·{@code CategoryNotEmptyException} ({@code quiz.category}) → 409,
 * {@code CategoryNotFoundException} → 404 (기존). 요청 검증: slug {@code ^[a-z0-9-]{1,50}$}, name 공백 불가·100자 이하. 400 문구는
 * D14 형식이다.
 */
@WebMvcTest(AdminCategoryController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class AdminCategoryControllerTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;
    private static final String COOKIE_NAME = "access_token";
    private static final String BASE = "/api/v1/admin/categories";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleService userRoleService;

    @MockitoBean
    AdminCategoryService adminCategoryService;

    @BeforeEach
    void setUp() {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);
    }

    @Test
    @DisplayName("카테고리 목록은 삭제 안 된 카테고리를 문제 수와 함께 JSON 배열로 반환한다 (API 계약)")
    void getCategories() throws Exception {
        given(adminCategoryService.getCategories())
                .willReturn(List.of(
                        new AdminCategoryResponse("java", "Java", "자바 기초", 10, 5),
                        new AdminCategoryResponse("kotlin", "Kotlin", null, 40, 0)));

        mvc.perform(asAdmin(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(
                        "$[0].keys()", containsInAnyOrder("slug", "name", "description", "sortOrder", "questionCount")))
                .andExpect(jsonPath("$[0].slug").value("java"))
                .andExpect(jsonPath("$[0].questionCount").value(5))
                .andExpect(jsonPath("$[1].description").value(nullValue()))
                .andDo(document(
                        "admin-categories-list",
                        requestCookies(cookieWithName(COOKIE_NAME).description("ADMIN 회원의 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].slug").description("카테고리 식별자"),
                                fieldWithPath("[].name").description("카테고리 이름"),
                                fieldWithPath("[].description").optional().description("설명 (없으면 null)"),
                                fieldWithPath("[].sortOrder").description("표시 순서 (작을수록 앞)"),
                                fieldWithPath("[].questionCount").description("삭제하지 않은 문제 수"))));
    }

    @Test
    @DisplayName("카테고리를 만들면 201과 만든 카테고리를 응답한다 (R4)")
    void createCategory() throws Exception {
        CategoryCreateRequest request = new CategoryCreateRequest("kotlin", "Kotlin", "코틀린 기초", 40);
        given(adminCategoryService.create(request))
                .willReturn(new AdminCategoryResponse("kotlin", "Kotlin", "코틀린 기초", 40, 0));

        mvc.perform(asAdmin(json(post(BASE), createBody("kotlin", "Kotlin"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("kotlin"))
                .andExpect(jsonPath("$.name").value("Kotlin"))
                .andExpect(jsonPath("$.description").value("코틀린 기초"))
                .andExpect(jsonPath("$.sortOrder").value(40))
                .andExpect(jsonPath("$.questionCount").value(0))
                .andDo(document(
                        "admin-categories-create",
                        requestFields(
                                fieldWithPath("slug").description("카테고리 식별자. 영문 소문자, 숫자, -로 1~50자. 나중에 바꿀 수 없다"),
                                fieldWithPath("name").description("카테고리 이름 (100자 이하)"),
                                fieldWithPath("description").optional().description("설명 (없으면 null)"),
                                fieldWithPath("sortOrder").description("표시 순서")),
                        responseFields(
                                fieldWithPath("slug").description("카테고리 식별자"),
                                fieldWithPath("name").description("카테고리 이름"),
                                fieldWithPath("description").description("설명"),
                                fieldWithPath("sortOrder").description("표시 순서"),
                                fieldWithPath("questionCount").description("삭제하지 않은 문제 수 (새 카테고리는 0)"))));

        then(adminCategoryService).should().create(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "a-1", "kotlin-coroutines", "abcdefghijklmnopqrstuvwxyz0123456789-abcdefghijklm"})
    @DisplayName("slug가 영문 소문자, 숫자, -로 1~50자면 받는다 (API 계약)")
    void acceptsValidSlug(String slug) throws Exception {
        given(adminCategoryService.create(new CategoryCreateRequest(slug, "Kotlin", "코틀린 기초", 40)))
                .willReturn(new AdminCategoryResponse(slug, "Kotlin", "코틀린 기초", 40, 0));

        mvc.perform(asAdmin(json(post(BASE), createBody(slug, "Kotlin")))).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "Kotlin",
                "kotlin_basic",
                "kotlin basic",
                "코틀린",
                "kotlin/basic",
                "abcdefghijklmnopqrstuvwxyz0123456789-abcdefghijklmn"
            })
    @DisplayName("slug가 형식에 맞지 않거나 50자를 넘으면 400이고 detail은 slug 항목을 알려 준다 (API 계약, D14)")
    void rejectsInvalidSlug(String slug) throws Exception {
        mvc.perform(asAdmin(json(post(BASE), createBody(slug, "Kotlin"))))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: slug)"));

        then(adminCategoryService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"slug\": \"kotlin\", \"sortOrder\": 40}",
                "{\"slug\": \"kotlin\", \"name\": \"   \", \"sortOrder\": 40}",
                "{\"name\": \"Kotlin\", \"sortOrder\": 40}",
                "{\"slug\": ",
                ""
            })
    @DisplayName("slug·name 누락, 공백 이름, 깨진 JSON, 빈 본문은 400 ProblemDetail이고 Service를 호출하지 않는다")
    void rejectsInvalidCreateBody(String body) throws Exception {
        mvc.perform(asAdmin(json(post(BASE), body))).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminCategoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("slug와 name이 함께 없으면 detail에 두 항목을 정렬해 담는다 (D14)")
    void reportsMissingFieldsInOrder() throws Exception {
        mvc.perform(asAdmin(json(post(BASE), "{\"sortOrder\": 40}")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: name, slug)"));
    }

    @Test
    @DisplayName("깨진 JSON은 400이고 detail은 요청 형식 문구다 (D14)")
    void rejectsBrokenJson() throws Exception {
        mvc.perform(asAdmin(json(post(BASE), "{\"slug\": ")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("이름이 100자를 넘으면 400이고 detail은 name 항목만 알려 주며 입력값은 담지 않는다 (D14)")
    void rejectsTooLongName() throws Exception {
        String name = "가".repeat(101);

        mvc.perform(asAdmin(json(post(BASE), createBody("kotlin", name))))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: name)"))
                .andExpect(content().string(not(containsString(name))))
                .andDo(document("admin-categories-create-invalid"));

        then(adminCategoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 있는 slug (삭제한 카테고리 포함)는 409 ProblemDetail로 응답한다 (R4, D7)")
    void createWithDuplicateSlug() throws Exception {
        DuplicateCategorySlugException exception = new DuplicateCategorySlugException("java");
        given(adminCategoryService.create(new CategoryCreateRequest("java", "Java", "코틀린 기초", 40)))
                .willThrow(exception);

        mvc.perform(asAdmin(json(post(BASE), createBody("java", "Java"))))
                .andExpect(problemDetail(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-categories-create-duplicate"));
    }

    @Test
    @DisplayName("카테고리를 고치면 200과 고친 카테고리를 응답한다. slug는 경로로만 받는다 (R4)")
    void updateCategory() throws Exception {
        CategoryUpdateRequest request = new CategoryUpdateRequest("자바", "자바 기초 문법", 5);
        given(adminCategoryService.update("java", request))
                .willReturn(new AdminCategoryResponse("java", "자바", "자바 기초 문법", 5, 5));

        mvc.perform(asAdmin(json(
                        put(BASE + "/{slug}", "java"),
                        "{\"name\": \"자바\", \"description\": \"자바 기초 문법\", \"sortOrder\": 5}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("java"))
                .andExpect(jsonPath("$.name").value("자바"))
                .andExpect(jsonPath("$.questionCount").value(5))
                .andDo(document(
                        "admin-categories-update",
                        pathParameters(parameterWithName("slug").description("고칠 카테고리의 식별자")),
                        requestFields(
                                fieldWithPath("name").description("카테고리 이름 (100자 이하)"),
                                fieldWithPath("description").optional().description("설명 (없으면 null)"),
                                fieldWithPath("sortOrder").description("표시 순서")),
                        responseFields(
                                fieldWithPath("slug").description("카테고리 식별자"),
                                fieldWithPath("name").description("카테고리 이름"),
                                fieldWithPath("description").description("설명"),
                                fieldWithPath("sortOrder").description("표시 순서"),
                                fieldWithPath("questionCount").description("삭제하지 않은 문제 수"))));

        then(adminCategoryService).should().update("java", request);
    }

    @Test
    @DisplayName("고칠 이름이 공백이면 400이고 Service를 호출하지 않는다 (D14)")
    void rejectsBlankNameOnUpdate() throws Exception {
        mvc.perform(asAdmin(json(put(BASE + "/{slug}", "java"), "{\"name\": \" \", \"sortOrder\": 5}")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: name)"));

        then(adminCategoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리를 고치면 404 ProblemDetail이다")
    void updateUnknownCategory() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("nope");
        given(adminCategoryService.update("nope", new CategoryUpdateRequest("이름", null, 0)))
                .willThrow(exception);

        mvc.perform(asAdmin(json(put(BASE + "/{slug}", "nope"), "{\"name\": \"이름\", \"sortOrder\": 0}")))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-categories-update-not-found"));
    }

    @Test
    @DisplayName("카테고리를 삭제하면 204 본문 없음이다 (R6)")
    void deleteCategory() throws Exception {
        mvc.perform(asAdmin(delete(BASE + "/{slug}", "kotlin")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andDo(document(
                        "admin-categories-delete",
                        pathParameters(parameterWithName("slug").description("삭제할 카테고리의 식별자"))));

        then(adminCategoryService).should().delete("kotlin");
    }

    @Test
    @DisplayName("삭제 안 된 문제가 남은 카테고리를 삭제하면 409 ProblemDetail이다 (R7)")
    void deleteCategoryWithQuestions() throws Exception {
        CategoryNotEmptyException exception = new CategoryNotEmptyException("java");
        willThrow(exception).given(adminCategoryService).delete("java");

        mvc.perform(asAdmin(delete(BASE + "/{slug}", "java")))
                .andExpect(problemDetail(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-categories-delete-not-empty"));
    }

    @Test
    @DisplayName("없거나 이미 삭제한 카테고리를 삭제하면 404 ProblemDetail이다")
    void deleteUnknownCategory() throws Exception {
        willThrow(new CategoryNotFoundException("nope"))
                .given(adminCategoryService)
                .delete("nope");

        mvc.perform(asAdmin(delete(BASE + "/{slug}", "nope"))).andExpect(problemDetail(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("비회원은 401 ProblemDetail이고 Service를 호출하지 않는다 (R2)")
    void rejectsGuest() throws Exception {
        mvc.perform(get(BASE)).andExpect(problemDetail(HttpStatus.UNAUTHORIZED)).andDo(document("admin-unauthorized"));

        then(adminCategoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("USER 회원은 403 ProblemDetail이고 Service를 호출하지 않는다 (R2, D4)")
    void rejectsUser() throws Exception {
        mvc.perform(get(BASE).cookie(new Cookie(COOKIE_NAME, TestJwts.valid(USER_ID))))
                .andExpect(problemDetail(HttpStatus.FORBIDDEN))
                .andDo(document("admin-forbidden"));

        then(adminCategoryService).shouldHaveNoInteractions();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(COOKIE_NAME, TestJwts.valid(ADMIN_ID)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String createBody(String slug, String name) {
        return "{\"slug\": \"%s\", \"name\": \"%s\", \"description\": \"코틀린 기초\", \"sortOrder\": 40}"
                .formatted(slug, name);
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
