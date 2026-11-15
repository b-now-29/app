package com.b.now._29.app.user.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.OwnerRoleChangeNotAllowedException;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserRoleService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * 관리자 회원 관리 API (docs/features/admin-members.md, role-owner-category-admin R2, R4, R9, API 계약).
 *
 * <ul>
 *   <li>{@code GET /api/v1/admin/users} → {@code List<AdminUserResponse>}
 *   <li>{@code GET /api/v1/admin/users/{id}} → {@code adminUserService.getUser(id)}. 없으면 {@code
 *       AdminUserNotFoundException} → 404
 *   <li>{@code PUT /api/v1/admin/users/{id}/role} {@code @Valid @RequestBody RoleChangeRequest(Role role, List<String>
 *       categorySlugs)} → {@code adminUserService.changeRole(id, role, categorySlugs)}. {@code categorySlugs}를 빼면 빈 목록을
 *       넘긴다. {@code @AssertTrue}로 OWNER 지정, 담당 없는 CATEGORY_ADMIN, 담당 있는 USER를 400으로 막는다
 *   <li>{@code AdminUserResponse(Long id, String email, Role role, Instant createdAt, String nickname, int tag,
 *       List<String> categorySlugs)}
 *   <li>{@code OwnerRoleChangeNotAllowedException} → 409, {@code CategoryNotFoundException} → 404
 * </ul>
 *
 * <p>권한: {@code /api/v1/admin/users/**}는 OWNER만 (SecurityConfig). 400 문구는 admin.md D14 형식.
 */
@WebMvcTest(AdminUserController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class AdminUserControllerTest {

    private static final long OWNER_ID = 1L;
    private static final long USER_ID = 2L;
    private static final long CATEGORY_ADMIN_ID = 3L;
    private static final String COOKIE_NAME = "access_token";
    private static final String BASE = "/api/v1/admin/users";
    private static final String USER_PATH = BASE + "/{id}";
    private static final String ROLE_PATH = BASE + "/{id}/role";
    private static final Instant CREATED_AT = Instant.parse("2026-11-13T05:12:30Z");

    private static final AdminUserResponse MANAGER = new AdminUserResponse(
            CATEGORY_ADMIN_ID,
            "manager@example.com",
            Role.CATEGORY_ADMIN,
            CREATED_AT,
            "철수",
            2,
            List.of("java", "spring"));

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleService userRoleService;

    @MockitoBean
    AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        given(userRoleService.findRole(OWNER_ID)).willReturn(Optional.of(Role.OWNER));
        given(userRoleService.findRole(USER_ID)).willReturn(Optional.of(Role.USER));
        given(userRoleService.findRole(CATEGORY_ADMIN_ID)).willReturn(Optional.of(Role.CATEGORY_ADMIN));
    }

    // 회원 목록

    @Test
    @DisplayName(
            "회원 목록은 id, email, role, createdAt, nickname, tag, categorySlugs를 JSON 배열로 반환하고 기존 회원의 createdAt은 null이다")
    void getUsers() throws Exception {
        given(adminUserService.getUsers())
                .willReturn(List.of(
                        new AdminUserResponse(OWNER_ID, "owner@example.com", Role.OWNER, null, "운영자", 1, List.of()),
                        MANAGER,
                        new AdminUserResponse(USER_ID, "test@example.com", Role.USER, CREATED_AT, "회원", 1, List.of())));

        mvc.perform(asOwner(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath(
                        "$[0].keys()",
                        containsInAnyOrder("id", "email", "role", "createdAt", "nickname", "tag", "categorySlugs")))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].createdAt").value(nullValue()))
                .andExpect(jsonPath("$[0].categorySlugs.length()").value(0))
                .andExpect(jsonPath("$[1].id").value(CATEGORY_ADMIN_ID))
                .andExpect(jsonPath("$[1].role").value("CATEGORY_ADMIN"))
                .andExpect(jsonPath("$[1].createdAt").value("2026-11-13T05:12:30Z"))
                .andExpect(jsonPath("$[1].nickname").value("철수"))
                .andExpect(jsonPath("$[1].tag").value(2))
                .andExpect(jsonPath("$[1].categorySlugs[0]").value("java"))
                .andExpect(jsonPath("$[1].categorySlugs[1]").value("spring"))
                .andExpect(jsonPath("$[2].role").value("USER"))
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
                .andDo(document(
                        "admin-users-list",
                        requestCookies(cookieWithName(COOKIE_NAME).description("OWNER 회원의 인증 쿠키")),
                        responseFields(userFields("[]."))));
    }

    @Test
    @DisplayName("회원이 없으면 빈 배열이다")
    void getUsersEmpty() throws Exception {
        given(adminUserService.getUsers()).willReturn(List.of());

        mvc.perform(asOwner(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // 회원 상세

    @Test
    @DisplayName("회원 한 명을 목록 항목과 같은 형식으로 반환한다 (API 계약)")
    void getUser() throws Exception {
        given(adminUserService.getUser(CATEGORY_ADMIN_ID)).willReturn(MANAGER);

        mvc.perform(asOwner(get(USER_PATH, CATEGORY_ADMIN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.keys()",
                        containsInAnyOrder("id", "email", "role", "createdAt", "nickname", "tag", "categorySlugs")))
                .andExpect(jsonPath("$.id").value(CATEGORY_ADMIN_ID))
                .andExpect(jsonPath("$.role").value("CATEGORY_ADMIN"))
                .andExpect(jsonPath("$.categorySlugs[0]").value("java"))
                .andExpect(jsonPath("$.categorySlugs[1]").value("spring"))
                .andDo(document(
                        "admin-users-get",
                        pathParameters(parameterWithName("id").description("회원 ID")),
                        responseFields(userFields(""))));
    }

    @Test
    @DisplayName("없는 회원을 조회하면 404 ProblemDetail이다 (API 계약)")
    void getUnknownUser() throws Exception {
        AdminUserNotFoundException exception = new AdminUserNotFoundException(999L);
        given(adminUserService.getUser(999L)).willThrow(exception);

        mvc.perform(asOwner(get(USER_PATH, 999L)))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-users-get-not-found"));
    }

    @Test
    @DisplayName("회원 id가 숫자가 아니면 상세 조회는 400이고 Service를 호출하지 않는다")
    void rejectsNonNumericIdOnGet() throws Exception {
        mvc.perform(asOwner(get(USER_PATH, "abc"))).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminUserService).shouldHaveNoInteractions();
    }

    // 역할과 담당 카테고리 변경 (R2, R9)

    @Test
    @DisplayName("CATEGORY_ADMIN과 담당 카테고리로 바꾸면 200과 바뀐 회원을 응답하고, 역할과 slug 목록을 Service에 넘긴다 (R9)")
    void changeRoleToCategoryAdmin() throws Exception {
        given(adminUserService.changeRole(CATEGORY_ADMIN_ID, Role.CATEGORY_ADMIN, List.of("java", "spring")))
                .willReturn(MANAGER);

        mvc.perform(asOwner(json(
                        put(ROLE_PATH, CATEGORY_ADMIN_ID),
                        "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"java\", \"spring\"]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.keys()",
                        containsInAnyOrder("id", "email", "role", "createdAt", "nickname", "tag", "categorySlugs")))
                .andExpect(jsonPath("$.role").value("CATEGORY_ADMIN"))
                .andExpect(jsonPath("$.categorySlugs[0]").value("java"))
                .andExpect(jsonPath("$.categorySlugs[1]").value("spring"))
                .andDo(document(
                        "admin-users-change-role",
                        pathParameters(parameterWithName("id").description("역할을 바꿀 회원의 ID")),
                        requestFields(
                                fieldWithPath("role").description("바꿀 역할. `USER` 또는 `CATEGORY_ADMIN` (`OWNER`는 400)"),
                                fieldWithPath("categorySlugs")
                                        .optional()
                                        .description("담당 카테고리 slug 목록. 지금 담당을 통째로 바꾼다. `CATEGORY_ADMIN`이면 1개 이상, "
                                                + "`USER`면 비우거나 뺀다 (빼면 빈 목록)")),
                        responseFields(userFields(""))));

        then(adminUserService).should().changeRole(CATEGORY_ADMIN_ID, Role.CATEGORY_ADMIN, List.of("java", "spring"));
    }

    @Test
    @DisplayName("USER로 바꿀 때 categorySlugs를 빼면 빈 목록으로 Service에 넘긴다 (API 계약)")
    void changeRoleToUserWithoutSlugs() throws Exception {
        given(adminUserService.changeRole(CATEGORY_ADMIN_ID, Role.USER, List.of()))
                .willReturn(new AdminUserResponse(
                        CATEGORY_ADMIN_ID, "manager@example.com", Role.USER, null, "철수", 2, List.of()));

        mvc.perform(asOwner(json(put(ROLE_PATH, CATEGORY_ADMIN_ID), "{\"role\": \"USER\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.categorySlugs.length()").value(0));

        then(adminUserService).should().changeRole(CATEGORY_ADMIN_ID, Role.USER, List.of());
    }

    @Test
    @DisplayName("USER로 바꿀 때 빈 categorySlugs도 받는다 (API 계약)")
    void changeRoleToUserWithEmptySlugs() throws Exception {
        given(adminUserService.changeRole(CATEGORY_ADMIN_ID, Role.USER, List.of()))
                .willReturn(new AdminUserResponse(
                        CATEGORY_ADMIN_ID, "manager@example.com", Role.USER, null, "철수", 2, List.of()));

        mvc.perform(asOwner(json(put(ROLE_PATH, CATEGORY_ADMIN_ID), "{\"role\": \"USER\", \"categorySlugs\": []}")))
                .andExpect(status().isOk());

        then(adminUserService).should().changeRole(CATEGORY_ADMIN_ID, Role.USER, List.of());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"role\": \"OWNER\"}",
                "{\"role\": \"OWNER\", \"categorySlugs\": [\"java\"]}",
                "{\"role\": \"CATEGORY_ADMIN\"}",
                "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": []}",
                "{\"role\": \"USER\", \"categorySlugs\": [\"java\"]}"
            })
    @DisplayName("OWNER 지정, 담당 없는 CATEGORY_ADMIN, 담당 있는 USER는 400 입력값 문구이고 Service를 호출하지 않는다 (R2, R9, API 계약)")
    void rejectsInvalidRoleAndSlugs(String body) throws Exception {
        mvc.perform(asOwner(json(put(ROLE_PATH, CATEGORY_ADMIN_ID), body)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail", startsWith("입력값이 올바르지 않습니다.")));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [null]}",
                "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"java\", null]}",
                "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"\"]}",
                "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"java\", \"   \"]}"
            })
    @DisplayName("categorySlugs에 null이나 공백 원소가 있으면 400 입력값 문구이고 Service를 호출하지 않는다 (API 계약: List<@NotBlank String>)")
    void rejectsNullOrBlankCategorySlug(String body) throws Exception {
        mvc.perform(asOwner(json(put(ROLE_PATH, CATEGORY_ADMIN_ID), body)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail", startsWith("입력값이 올바르지 않습니다.")))
                .andExpect(jsonPath("$.detail", containsString("categorySlugs")));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("담당 없는 CATEGORY_ADMIN 요청의 400을 문서화한다 (R9)")
    void documentsCategoryAdminWithoutSlugs() throws Exception {
        mvc.perform(asOwner(json(
                        put(ROLE_PATH, CATEGORY_ADMIN_ID), "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": []}")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andDo(document("admin-users-change-role-invalid"));
    }

    @Test
    @DisplayName("대상이 OWNER면 409 ProblemDetail이다 (R2)")
    void rejectsChangingOwner() throws Exception {
        given(adminUserService.changeRole(OWNER_ID, Role.USER, List.of()))
                .willThrow(new OwnerRoleChangeNotAllowedException());

        mvc.perform(asOwner(json(put(ROLE_PATH, OWNER_ID), "{\"role\": \"USER\"}")))
                .andExpect(problemDetail(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value("운영자의 역할은 바꿀 수 없습니다."))
                .andDo(document("admin-users-change-role-owner"));
    }

    @Test
    @DisplayName("없거나 삭제된 카테고리 slug가 있으면 404 ProblemDetail이다 (API 계약)")
    void rejectsUnknownCategorySlug() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("nope");
        given(adminUserService.changeRole(CATEGORY_ADMIN_ID, Role.CATEGORY_ADMIN, List.of("java", "nope")))
                .willThrow(exception);

        mvc.perform(asOwner(json(
                        put(ROLE_PATH, CATEGORY_ADMIN_ID),
                        "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"java\", \"nope\"]}")))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-users-change-role-category-not-found"));
    }

    @Test
    @DisplayName("없는 회원이면 404 ProblemDetail이다")
    void changeRoleOfUnknownUser() throws Exception {
        AdminUserNotFoundException exception = new AdminUserNotFoundException(999L);
        given(adminUserService.changeRole(999L, Role.USER, List.of())).willThrow(exception);

        mvc.perform(asOwner(json(put(ROLE_PATH, 999L), "{\"role\": \"USER\"}")))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-users-change-role-not-found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\": null}"})
    @DisplayName("role이 없으면 400이고 detail은 role 항목을 알려 주며 Service를 호출하지 않는다 (D14)")
    void rejectsMissingRole(String body) throws Exception {
        mvc.perform(asOwner(json(put(ROLE_PATH, CATEGORY_ADMIN_ID), body)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail", startsWith("입력값이 올바르지 않습니다.")))
                .andExpect(jsonPath("$.detail", containsString("role")));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"role\": \"ADMIN\"}", "{\"role\": \"\"}", "{\"role\": 1}", "{\"role\": ", ""})
    @DisplayName("없는 role 값 (옛 ADMIN 포함), 숫자, 깨진 JSON, 빈 본문은 400 요청 형식 문구이고 입력값을 담지 않는다 (R1, D14)")
    void rejectsUnreadableRole(String body) throws Exception {
        mvc.perform(asOwner(json(put(ROLE_PATH, CATEGORY_ADMIN_ID), body)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("요청 형식이 올바르지 않습니다."))
                .andExpect(content().string(not(containsString("ADMIN"))));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원 id가 숫자가 아니면 400 ProblemDetail이고 Service를 호출하지 않는다")
    void rejectsNonNumericId() throws Exception {
        mvc.perform(asOwner(json(put(ROLE_PATH, "abc"), "{\"role\": \"USER\"}")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminUserService).shouldHaveNoInteractions();
    }

    // 권한 (R4: 회원 관리는 OWNER만)

    @Test
    @DisplayName("비회원은 목록·상세·역할 변경이 401이고 Service를 호출하지 않는다")
    void rejectsGuest() throws Exception {
        mvc.perform(get(BASE)).andExpect(problemDetail(HttpStatus.UNAUTHORIZED));
        mvc.perform(get(USER_PATH, USER_ID)).andExpect(problemDetail(HttpStatus.UNAUTHORIZED));
        mvc.perform(json(put(ROLE_PATH, USER_ID), "{\"role\": \"USER\"}"))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("USER 회원은 목록·상세·역할 변경이 403이고 Service를 호출하지 않는다")
    void rejectsUser() throws Exception {
        mvc.perform(as(USER_ID, get(BASE))).andExpect(problemDetail(HttpStatus.FORBIDDEN));
        mvc.perform(as(USER_ID, get(USER_PATH, USER_ID))).andExpect(problemDetail(HttpStatus.FORBIDDEN));
        mvc.perform(as(
                        USER_ID,
                        json(put(ROLE_PATH, USER_ID), "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"java\"]}")))
                .andExpect(problemDetail(HttpStatus.FORBIDDEN));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CATEGORY_ADMIN도 회원 관리 API는 403이고 Service를 호출하지 않는다 (R4)")
    void rejectsCategoryAdmin() throws Exception {
        mvc.perform(as(CATEGORY_ADMIN_ID, get(BASE))).andExpect(problemDetail(HttpStatus.FORBIDDEN));
        mvc.perform(as(CATEGORY_ADMIN_ID, get(USER_PATH, USER_ID))).andExpect(problemDetail(HttpStatus.FORBIDDEN));
        mvc.perform(as(
                        CATEGORY_ADMIN_ID,
                        json(put(ROLE_PATH, USER_ID), "{\"role\": \"CATEGORY_ADMIN\", \"categorySlugs\": [\"java\"]}")))
                .andExpect(problemDetail(HttpStatus.FORBIDDEN));

        then(adminUserService).shouldHaveNoInteractions();
    }

    private static FieldDescriptor[] userFields(String prefix) {
        return new FieldDescriptor[] {
            fieldWithPath(prefix + "id").description("회원 ID"),
            fieldWithPath(prefix + "email").description("이메일"),
            fieldWithPath(prefix + "role").description("`USER`, `CATEGORY_ADMIN`, `OWNER` 중 하나"),
            fieldWithPath(prefix + "createdAt")
                    .optional()
                    .description("가입 시각 (ISO-8601 UTC). 가입 시각 저장 전에 가입한 회원은 null"),
            fieldWithPath(prefix + "nickname").description("닉네임"),
            fieldWithPath(prefix + "tag").description("같은 닉네임 안에서 회원을 구분하는 번호. 화면에는 `닉네임#번호`로 표시한다"),
            fieldWithPath(prefix + "categorySlugs")
                    .description("삭제되지 않은 담당 카테고리 slug 목록 (카테고리 표시 순서). `CATEGORY_ADMIN`이 아니면 빈 배열")
        };
    }

    private static MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) {
        return as(OWNER_ID, request);
    }

    private static MockHttpServletRequestBuilder as(long userId, MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(COOKIE_NAME, TestJwts.valid(userId)));
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
