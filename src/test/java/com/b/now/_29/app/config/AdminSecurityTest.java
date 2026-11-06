package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.admin.AdminCategoryController;
import com.b.now._29.app.quiz.admin.AdminCategoryService;
import com.b.now._29.app.quiz.admin.AdminQuestionController;
import com.b.now._29.app.quiz.admin.AdminQuestionService;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.UserRoleService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code /api/v1/admin/**}의 인가 (R2, D3, D4). 401은 기존 {@code AuthenticationEntryPoint}, 403은 같은 형식의 {@code
 * AccessDeniedHandler}가 쓴다. role은 JWT가 아니라 요청마다 {@code UserRoleService.isAdmin(userId)}로 확인한다.
 */
@WebMvcTest({AdminCategoryController.class, AdminQuestionController.class})
@WithAppSecurity
class AdminSecurityTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;
    private static final String COOKIE_NAME = "access_token";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleService userRoleService;

    @MockitoBean
    AdminCategoryService adminCategoryService;

    @MockitoBean
    AdminQuestionService adminQuestionService;

    static Stream<Arguments> adminEndpoints() {
        String categoryBody = "{\"slug\": \"kotlin\", \"name\": \"Kotlin\", \"sortOrder\": 0}";
        String questionBody = """
                {"categorySlug": "java", "type": "ESSAY", "content": "문제", "modelAnswer": "답",
                 "sortOrder": 0, "choices": []}
                """;
        return Stream.of(
                arguments(HttpMethod.GET, "/api/v1/admin/categories", null),
                arguments(HttpMethod.POST, "/api/v1/admin/categories", categoryBody),
                arguments(HttpMethod.PUT, "/api/v1/admin/categories/java", categoryBody),
                arguments(HttpMethod.DELETE, "/api/v1/admin/categories/java", null),
                arguments(HttpMethod.GET, "/api/v1/admin/questions?category=java", null),
                arguments(HttpMethod.GET, "/api/v1/admin/questions/1", null),
                arguments(HttpMethod.POST, "/api/v1/admin/questions", questionBody),
                arguments(HttpMethod.PUT, "/api/v1/admin/questions/1", questionBody),
                arguments(HttpMethod.DELETE, "/api/v1/admin/questions/1", null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    @DisplayName("비회원은 모든 관리자 API에서 401 ProblemDetail이고 Service를 호출하지 않는다 (R2)")
    void guestIsUnauthorized(HttpMethod method, String path, String body) throws Exception {
        perform(method, path, body, null)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").exists());

        then(adminCategoryService).shouldHaveNoInteractions();
        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    @DisplayName("토큰이 만료된 사용자는 관리자여도 401이다 (R2)")
    void expiredTokenIsUnauthorized(HttpMethod method, String path, String body) throws Exception {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);

        perform(method, path, body, TestJwts.expired(ADMIN_ID)).andExpect(status().isUnauthorized());

        then(adminCategoryService).shouldHaveNoInteractions();
        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    @DisplayName("USER 회원은 모든 관리자 API에서 403 ProblemDetail이고 Service를 호출하지 않는다 (R2, D4)")
    void userIsForbidden(HttpMethod method, String path, String body) throws Exception {
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);

        perform(method, path, body, TestJwts.valid(USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                // 401과 같은 형식: instance에 요청 경로를 담는다
                .andExpect(jsonPath("$.instance").value(path.replaceFirst("\\?.*", "")));

        then(adminCategoryService).shouldHaveNoInteractions();
        then(adminQuestionService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    @DisplayName("ADMIN 회원은 모든 관리자 API의 인가를 통과한다 (R2)")
    void adminPasses(HttpMethod method, String path, String body) throws Exception {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);

        int status = perform(method, path, body, TestJwts.valid(ADMIN_ID))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("role은 토큰의 사용자 ID로 DB에서 확인한다 (D3)")
    void checksRoleOfTokenUser() throws Exception {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);
        given(adminCategoryService.getCategories()).willReturn(List.of());

        perform(HttpMethod.GET, "/api/v1/admin/categories", null, TestJwts.valid(ADMIN_ID))
                .andExpect(status().isOk());

        then(userRoleService).should().isAdmin(ADMIN_ID);
    }

    @Test
    @DisplayName("role을 요청마다 다시 확인하므로, 같은 토큰이라도 ADMIN에서 USER로 바뀌면 바로 403이다 (D3)")
    void roleChangeTakesEffectImmediately() throws Exception {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true, false);
        given(adminCategoryService.getCategories()).willReturn(List.of());
        String token = TestJwts.valid(ADMIN_ID);

        perform(HttpMethod.GET, "/api/v1/admin/categories", null, token).andExpect(status().isOk());
        perform(HttpMethod.GET, "/api/v1/admin/categories", null, token).andExpect(status().isForbidden());

        then(userRoleService).should(times(2)).isAdmin(ADMIN_ID);
    }

    @Test
    @DisplayName("관리자가 아닌 경로는 role을 확인하지 않는다 (D3: 관리자 요청에서만 DB 조회)")
    void nonAdminPathDoesNotCheckRole() throws Exception {
        perform(HttpMethod.GET, "/api/v1/users/me", null, null).andExpect(status().isUnauthorized());
        perform(HttpMethod.GET, "/api/v1/categories", null, TestJwts.valid(USER_ID));

        then(userRoleService).shouldHaveNoInteractions();
    }

    private ResultActions perform(HttpMethod method, String path, String body, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            builder.cookie(new Cookie(COOKIE_NAME, token));
        }
        return mvc.perform(builder);
    }
}
