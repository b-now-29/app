package com.b.now._29.app.user.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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

import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.SelfDemotionNotAllowedException;
import com.b.now._29.app.user.UserRoleService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
 * 관리자 회원 관리 API (docs/features/admin-members.md R1, R3~R6, API 계약, D2, D3, D6).
 *
 * <p>구현할 것 (패키지 {@code com.b.now._29.app.user.admin}, 의존 방향 {@code user.admin → user}):
 *
 * <ul>
 *   <li>{@code AdminUserController} — {@code /api/v1/admin/users}. 요청한 관리자 id는 {@code @AuthenticationPrincipal Long}
 *       <ul>
 *         <li>{@code GET /api/v1/admin/users} → {@code List<AdminUserResponse>}
 *         <li>{@code PUT /api/v1/admin/users/{id}/role} {@code @Valid @RequestBody RoleChangeRequest} → {@code
 *             AdminUserResponse}
 *       </ul>
 *   <li>{@code AdminUserService}
 *       <ul>
 *         <li>{@code List<AdminUserResponse> getUsers()} — id 오름차순 ({@code UserRepository.findAllByOrderByIdAsc()})
 *         <li>{@code AdminUserResponse changeRole(long id, Role role, long actorId)} — 없으면 {@code
 *             AdminUserNotFoundException}, 자기 해제는 {@code User.changeRole}이 {@code SelfDemotionNotAllowedException}
 *       </ul>
 *   <li>{@code record AdminUserResponse(Long id, String email, Role role, Instant createdAt, String nickname, int tag)} —
 *       createdAt은 null 가능 (D6). nickname, tag는 nickname.md R11에서 뒤에 추가
 *   <li>{@code record RoleChangeRequest(@NotNull Role role)}
 *   <li>{@code AdminUserNotFoundException(long id)} → 404. 기존 {@code UserNotFoundException}은 401로 매핑돼 있어 쓰지 않는다
 *   <li>{@code ApiExceptionHandler}: {@code AdminUserNotFoundException} → 404, {@code SelfDemotionNotAllowedException} → 409
 * </ul>
 *
 * <p>권한 (R6): 기존 {@code /api/v1/admin/**} 규칙 그대로 (SecurityConfig 변경 없음). 400 문구는 admin.md D14 형식.
 */
@WebMvcTest(AdminUserController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class AdminUserControllerTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;
    private static final String COOKIE_NAME = "access_token";
    private static final String BASE = "/api/v1/admin/users";
    private static final String ROLE_PATH = BASE + "/{id}/role";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRoleService userRoleService;

    @MockitoBean
    AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        given(userRoleService.isAdmin(ADMIN_ID)).willReturn(true);
    }

    // 회원 목록 (R1, R2)

    @Test
    @DisplayName(
            "회원 목록은 id, email, role, createdAt, nickname, tag만 JSON 배열로 반환하고 기존 회원의 createdAt은 null이다 (R1, R2, nickname R11)")
    void getUsers() throws Exception {
        given(adminUserService.getUsers())
                .willReturn(List.of(
                        new AdminUserResponse(1L, "test@example.com", Role.USER, null, "회원", 1),
                        new AdminUserResponse(
                                3L, "new@example.com", Role.ADMIN, Instant.parse("2026-11-13T05:12:30Z"), "철수", 2)));

        mvc.perform(asAdmin(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(
                        "$[0].keys()", containsInAnyOrder("id", "email", "role", "createdAt", "nickname", "tag")))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].email").value("test@example.com"))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].createdAt").value(nullValue()))
                .andExpect(jsonPath("$[0].nickname").value("회원"))
                .andExpect(jsonPath("$[0].tag").value(1))
                .andExpect(jsonPath("$[1].id").value(3))
                .andExpect(jsonPath("$[1].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].createdAt").value("2026-11-13T05:12:30Z"))
                .andExpect(jsonPath("$[1].nickname").value("철수"))
                .andExpect(jsonPath("$[1].tag").value(2))
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
                .andDo(document(
                        "admin-users-list",
                        requestCookies(cookieWithName(COOKIE_NAME).description("ADMIN 회원의 인증 쿠키")),
                        responseFields(
                                fieldWithPath("[].id").description("회원 ID"),
                                fieldWithPath("[].email").description("이메일"),
                                fieldWithPath("[].role").description("`USER` 또는 `ADMIN`"),
                                fieldWithPath("[].createdAt")
                                        .optional()
                                        .description("가입 시각 (ISO-8601 UTC). 가입 시각 저장 전에 가입한 회원은 null"),
                                fieldWithPath("[].nickname").description("닉네임"),
                                fieldWithPath("[].tag").description("같은 닉네임 안에서 회원을 구분하는 번호. 화면에는 `닉네임#번호`로 표시한다"))));
    }

    @Test
    @DisplayName("회원이 없으면 빈 배열이다")
    void getUsersEmpty() throws Exception {
        given(adminUserService.getUsers()).willReturn(List.of());

        mvc.perform(asAdmin(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // role 변경 (R3, R4, R5)

    @Test
    @DisplayName("다른 회원의 role을 바꾸면 200과 바뀐 회원 1건을 목록 항목과 같은 형식으로 응답한다. 요청한 관리자 id를 Service에 넘긴다 (R3)")
    void changeRole() throws Exception {
        given(adminUserService.changeRole(3L, Role.ADMIN, ADMIN_ID))
                .willReturn(new AdminUserResponse(
                        3L, "new@example.com", Role.ADMIN, Instant.parse("2026-11-13T05:12:30Z"), "철수", 2));

        mvc.perform(asAdmin(json(put(ROLE_PATH, 3L), "{\"role\": \"ADMIN\"}")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.keys()", containsInAnyOrder("id", "email", "role", "createdAt", "nickname", "tag")))
                .andExpect(jsonPath("$.nickname").value("철수"))
                .andExpect(jsonPath("$.tag").value(2))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-13T05:12:30Z"))
                .andDo(document(
                        "admin-users-change-role",
                        pathParameters(parameterWithName("id").description("role을 바꿀 회원의 ID")),
                        requestFields(fieldWithPath("role").description("바꿀 role. `USER` 또는 `ADMIN`")),
                        responseFields(
                                fieldWithPath("id").description("회원 ID"),
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("role").description("바뀐 role"),
                                fieldWithPath("createdAt").optional().description("가입 시각 (ISO-8601 UTC, 없으면 null)"),
                                fieldWithPath("nickname").description("닉네임"),
                                fieldWithPath("tag").description("같은 닉네임 안에서 회원을 구분하는 번호"))));

        then(adminUserService).should().changeRole(3L, Role.ADMIN, ADMIN_ID);
    }

    @Test
    @DisplayName("관리자를 USER로 해제할 수 있다 (R3)")
    void demoteOtherAdmin() throws Exception {
        given(adminUserService.changeRole(3L, Role.USER, ADMIN_ID))
                .willReturn(new AdminUserResponse(3L, "new@example.com", Role.USER, null, "철수", 2));

        mvc.perform(asAdmin(json(put(ROLE_PATH, 3L), "{\"role\": \"USER\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value(nullValue()));
    }

    @Test
    @DisplayName("자기 자신을 ADMIN으로 지정하는 요청은 200이다 (R4)")
    void keepSelfAdmin() throws Exception {
        given(adminUserService.changeRole(ADMIN_ID, Role.ADMIN, ADMIN_ID))
                .willReturn(new AdminUserResponse(ADMIN_ID, "admin@example.com", Role.ADMIN, null, "관리자", 1));

        mvc.perform(asAdmin(json(put(ROLE_PATH, ADMIN_ID), "{\"role\": \"ADMIN\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("자기 자신을 USER로 바꾸면 409 ProblemDetail이다 (R4, D2)")
    void rejectsSelfDemotion() throws Exception {
        SelfDemotionNotAllowedException exception = new SelfDemotionNotAllowedException(ADMIN_ID);
        given(adminUserService.changeRole(ADMIN_ID, Role.USER, ADMIN_ID)).willThrow(exception);

        mvc.perform(asAdmin(json(put(ROLE_PATH, ADMIN_ID), "{\"role\": \"USER\"}")))
                .andExpect(problemDetail(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-users-change-role-self"));
    }

    @Test
    @DisplayName("없는 회원이면 404 ProblemDetail이다 (R5)")
    void changeRoleOfUnknownUser() throws Exception {
        AdminUserNotFoundException exception = new AdminUserNotFoundException(999L);
        given(adminUserService.changeRole(999L, Role.ADMIN, ADMIN_ID)).willThrow(exception);

        mvc.perform(asAdmin(json(put(ROLE_PATH, 999L), "{\"role\": \"ADMIN\"}")))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("admin-users-change-role-not-found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\": null}"})
    @DisplayName("role이 없으면 400이고 detail은 role 항목을 알려 주며 Service를 호출하지 않는다 (R5, D14)")
    void rejectsMissingRole(String body) throws Exception {
        mvc.perform(asAdmin(json(put(ROLE_PATH, 3L), body)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("입력값이 올바르지 않습니다. (항목: role)"))
                .andDo(document("admin-users-change-role-invalid"));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"role\": \"OWNER\"}", "{\"role\": \"\"}", "{\"role\": 1}", "{\"role\": ", ""})
    @DisplayName("없는 role 값, 타입 불일치, 깨진 JSON, 빈 본문은 400 요청 형식 문구이고 입력값을 담지 않는다 (R5, D14)")
    void rejectsUnreadableRole(String body) throws Exception {
        mvc.perform(asAdmin(json(put(ROLE_PATH, 3L), body)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("요청 형식이 올바르지 않습니다."))
                .andExpect(content().string(not(containsString("OWNER"))));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원 id가 숫자가 아니면 400 ProblemDetail이고 Service를 호출하지 않는다")
    void rejectsNonNumericId() throws Exception {
        mvc.perform(asAdmin(json(put(ROLE_PATH, "abc"), "{\"role\": \"ADMIN\"}")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(adminUserService).shouldHaveNoInteractions();
    }

    // 권한 (R6)

    @Test
    @DisplayName("비회원은 목록 조회가 401이고 Service를 호출하지 않는다 (R6)")
    void rejectsGuestList() throws Exception {
        mvc.perform(get(BASE)).andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비회원은 role 변경이 401이고 Service를 호출하지 않는다 (R6)")
    void rejectsGuestChange() throws Exception {
        mvc.perform(json(put(ROLE_PATH, 3L), "{\"role\": \"ADMIN\"}"))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("USER 회원은 목록 조회가 403이고 Service를 호출하지 않는다 (R6)")
    void rejectsUserList() throws Exception {
        mvc.perform(asUser(get(BASE))).andExpect(problemDetail(HttpStatus.FORBIDDEN));

        then(adminUserService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("USER 회원이 자기 자신을 ADMIN으로 바꾸려 해도 403이고 Service를 호출하지 않는다 (R6)")
    void rejectsUserSelfPromotion() throws Exception {
        mvc.perform(asUser(json(put(ROLE_PATH, USER_ID), "{\"role\": \"ADMIN\"}")))
                .andExpect(problemDetail(HttpStatus.FORBIDDEN));

        then(adminUserService).shouldHaveNoInteractions();
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(COOKIE_NAME, TestJwts.valid(ADMIN_ID)));
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(COOKIE_NAME, TestJwts.valid(USER_ID)));
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
