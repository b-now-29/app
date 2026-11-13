package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.support.SetCookieHeader;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(AuthController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class AuthControllerTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password1234";
    private static final String CREDENTIALS = "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, PASSWORD);

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenService tokenService;

    @MockitoBean
    UserService userService;

    @Test
    @DisplayName("로그인하면 200, UserResponse, 인증 쿠키를 응답한다")
    void login() throws Exception {
        given(userService.login(EMAIL, PASSWORD)).willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 3));

        MvcResult result = postLogin(CREDENTIALS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email", "role", "nickname", "tag")))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.nickname").value("철수"))
                .andExpect(jsonPath("$.tag").value(3))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andDo(document(
                        "auth-login",
                        requestFields(
                                fieldWithPath("email").description("이메일. 대소문자와 앞뒤 공백은 구분하지 않는다"),
                                fieldWithPath("password").description("비밀번호")),
                        responseHeaders(headerWithName(HttpHeaders.SET_COOKIE).description("인증 쿠키 access_token")),
                        responseFields(
                                fieldWithPath("id").description("사용자 ID"),
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("role").description("권한 (USER, ADMIN)"),
                                fieldWithPath("nickname").description("닉네임"),
                                fieldWithPath("tag").description("같은 닉네임 안에서 회원을 구분하는 번호"))))
                .andReturn();

        SetCookieHeader cookie = SetCookieHeader.from(result);
        assertThat(cookie.name()).isEqualTo("access_token");
        assertThat(cookie.attributes()).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=604800");
        assertThat(tokenService.parseUserId(cookie.value())).contains(USER_ID);
    }

    @Test
    @DisplayName("앞뒤 공백과 대문자가 있는 이메일은 형식 오류가 아니다 (R2)")
    void loginAcceptsUnnormalizedEmail() throws Exception {
        given(userService.login(anyString(), eq(PASSWORD)))
                .willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 3));

        postLogin("{\"email\": \"  User@Example.COM \", \"password\": \"password1234\"}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 실패는 401 ProblemDetail이고 쿠키를 주지 않는다 (R5)")
    void loginFailure() throws Exception {
        InvalidCredentialsException exception = new InvalidCredentialsException();
        given(userService.login(EMAIL, PASSWORD)).willThrow(exception);

        MvcResult result = postLogin(CREDENTIALS)
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("auth-login-unauthorized"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"email\": \"user@example.com\"}",
                "{\"password\": \"password1234\"}",
                "{\"email\": null, \"password\": \"password1234\"}",
                "{\"email\": \"user@example.com\", \"password\": null}",
                "{\"email\": \"\", \"password\": \"password1234\"}",
                "{\"email\": \"user@example.com\", \"password\": \"\"}",
                "{\"email\": ",
                ""
            })
    @DisplayName("필드 누락, 빈 값, 깨진 JSON, 빈 본문은 400 ProblemDetail이고 Service를 호출하지 않는다")
    void loginWithInvalidBody(String body) throws Exception {
        postLogin(body).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이메일과 비밀번호가 모두 비어 있으면 400이고 Service를 호출하지 않는다 (D13)")
    void loginWithBothFieldsEmpty() throws Exception {
        postLogin("{\"email\": \"\", \"password\": \"\"}").andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(userService).shouldHaveNoInteractions();
    }

    static Stream<Arguments> credentialsBreakingSignUpRules() {
        return Stream.of(
                arguments("이메일 형식이 아닌 값", "not-an-email", PASSWORD),
                arguments("8자보다 짧은 비밀번호", EMAIL, "short"),
                arguments("73바이트 비밀번호", EMAIL, "a".repeat(73)),
                arguments("25자지만 75바이트인 한글 비밀번호", EMAIL, "가".repeat(25)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("credentialsBreakingSignUpRules")
    @DisplayName("가입 규칙에 맞지 않는 값도 400이 아니라 Service까지 전달되고, 인증 실패면 401이다 (D13)")
    void loginDoesNotApplySignUpRules(String description, String email, String password) throws Exception {
        InvalidCredentialsException exception = new InvalidCredentialsException();
        given(userService.login(email, password)).willThrow(exception);

        MvcResult result = postLogin("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andExpect(content().string(not(containsString(password))))
                .andReturn();

        then(userService).should().login(email, password);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("로그인 형식 오류의 응답을 문서화한다")
    void documentsInvalidLogin() throws Exception {
        postLogin("{}").andExpect(problemDetail(HttpStatus.BAD_REQUEST)).andDo(document("auth-login-invalid"));
    }

    @Test
    @DisplayName("로그아웃하면 204이고 같은 속성으로 쿠키를 지운다 (R7)")
    void logout() throws Exception {
        MvcResult result = mvc.perform(
                        post("/api/v1/auth/logout").cookie(new Cookie("access_token", TestJwts.valid(USER_ID))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andDo(document(
                        "auth-logout",
                        responseHeaders(headerWithName(HttpHeaders.SET_COOKIE).description("Max-Age=0으로 인증 쿠키를 지운다"))))
                .andReturn();

        assertExpiredCookie(SetCookieHeader.from(result));
    }

    @Test
    @DisplayName("비로그인 상태에서 로그아웃해도 204이고 쿠키를 지운다")
    void logoutWithoutLogin() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertExpiredCookie(SetCookieHeader.from(result));
    }

    private static void assertExpiredCookie(SetCookieHeader cookie) {
        assertThat(cookie.name()).isEqualTo("access_token");
        assertThat(cookie.value()).isEmpty();
        assertThat(cookie.attributes()).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=0");
    }

    private ResultActions postLogin(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
