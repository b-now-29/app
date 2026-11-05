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
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
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

@WebMvcTest(UserController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class UserControllerTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password1234";
    private static final String HANGUL_SYLLABLE = "가"; // UTF-8 3바이트
    private static final int MAX_EMAIL_LENGTH = 255;

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenService tokenService;

    @MockitoBean
    UserService userService;

    @Test
    @DisplayName("가입하면 201, UserResponse, 바로 로그인되는 쿠키를 응답한다 (R1)")
    void signUp() throws Exception {
        given(userService.signUp(EMAIL, PASSWORD)).willReturn(new UserResponse(USER_ID, EMAIL));

        MvcResult result = postUser(body(EMAIL, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email")))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andDo(document(
                        "users-signup",
                        requestFields(
                                fieldWithPath("email").description("이메일. 앞뒤 공백을 지우고 소문자로 저장한다"),
                                fieldWithPath("password").description("비밀번호. 8자 이상, UTF-8 72바이트 이하")),
                        responseHeaders(headerWithName(HttpHeaders.SET_COOKIE).description("인증 쿠키 access_token")),
                        responseFields(
                                fieldWithPath("id").description("사용자 ID"),
                                fieldWithPath("email").description("정규화된 이메일"))))
                .andReturn();

        SetCookieHeader cookie = SetCookieHeader.from(result);
        assertThat(cookie.name()).isEqualTo("access_token");
        assertThat(cookie.attributes()).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=604800");
        assertThat(tokenService.parseUserId(cookie.value())).contains(USER_ID);
    }

    @Test
    @DisplayName("앞뒤 공백과 대문자가 있는 이메일은 형식 오류가 아니다 (R2)")
    void signUpAcceptsUnnormalizedEmail() throws Exception {
        given(userService.signUp(anyString(), eq(PASSWORD))).willReturn(new UserResponse(USER_ID, EMAIL));

        postUser(body("  User@Example.COM ", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("중복 이메일은 409 ProblemDetail이고 쿠키를 주지 않는다 (R2)")
    void signUpWithDuplicateEmail() throws Exception {
        DuplicateEmailException exception = new DuplicateEmailException(EMAIL);
        given(userService.signUp(EMAIL, PASSWORD)).willThrow(exception);

        MvcResult result = postUser(body(EMAIL, PASSWORD))
                .andExpect(problemDetail(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("users-signup-duplicate-email"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    static Stream<String> validPasswords() {
        return Stream.of(
                "a".repeat(8), // 최소 길이
                "a".repeat(72), // 최대 바이트
                HANGUL_SYLLABLE.repeat(8), // 8자, 24바이트
                HANGUL_SYLLABLE.repeat(24)); // 24자, 정확히 72바이트
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    @DisplayName("8자 이상, UTF-8 72바이트 이하의 비밀번호는 가입할 수 있다 (R3)")
    void signUpWithValidPassword(String password) throws Exception {
        given(userService.signUp(EMAIL, password)).willReturn(new UserResponse(USER_ID, EMAIL));

        postUser(body(EMAIL, password)).andExpect(status().isCreated());
    }

    static Stream<String> invalidPasswords() {
        return Stream.of(
                "a".repeat(7), // 7자
                HANGUL_SYLLABLE.repeat(7), // 21바이트지만 7자
                "a".repeat(73), // 73바이트
                HANGUL_SYLLABLE.repeat(25)); // 25자지만 75바이트
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    @DisplayName("7자 이하이거나 UTF-8 72바이트를 넘는 비밀번호는 400이고 비밀번호를 응답에 담지 않는다 (R3, R4)")
    void signUpWithInvalidPassword(String password) throws Exception {
        postUser(body(EMAIL, password))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(content().string(not(containsString(password))));

        then(userService).shouldHaveNoInteractions();
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
                "{\"email\": \"   \", \"password\": \"password1234\"}",
                "{\"email\": \"not-an-email\", \"password\": \"password1234\"}",
                "{\"email\": ",
                ""
            })
    @DisplayName("필드 누락, 이메일 형식 오류, 깨진 JSON, 빈 본문은 400 ProblemDetail이고 Service를 호출하지 않는다")
    void signUpWithInvalidBody(String requestBody) throws Exception {
        postUser(requestBody).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(userService).shouldHaveNoInteractions();
    }

    static Stream<Arguments> singleFieldViolations() {
        return Stream.of(
                arguments(EMAIL, "a".repeat(7), "비밀번호", "이메일"), // 짧은 비밀번호
                arguments(EMAIL, "a".repeat(73), "비밀번호", "이메일"), // 72바이트 초과
                arguments(EMAIL, HANGUL_SYLLABLE.repeat(25), "비밀번호", "이메일"), // 25자지만 75바이트
                arguments("not-an-email", PASSWORD, "이메일", "비밀번호")); // 잘못된 이메일
    }

    @ParameterizedTest
    @MethodSource("singleFieldViolations")
    @DisplayName("가입 검증 실패의 detail은 실패한 필드를 알려 주는 한국어 문구다 (D16)")
    void signUpValidationDetailNamesField(String email, String password, String failedField, String validField)
            throws Exception {
        postUser(body(email, password))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail", containsString(failedField)))
                .andExpect(jsonPath("$.detail", not(containsString(validField))))
                .andExpect(jsonPath("$.detail", not(containsString("Invalid request content."))));
    }

    @Test
    @DisplayName("255자를 넘는 이메일은 400이고 Service를 호출하지 않는다 (D19)")
    void signUpWithTooLongEmail() throws Exception {
        postUser(body(emailOfLength(MAX_EMAIL_LENGTH + 1), PASSWORD)).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정확히 255자인 형식상 유효한 이메일은 가입할 수 있다 (D19)")
    void signUpWithEmailAtMaxLength() throws Exception {
        String email = emailOfLength(MAX_EMAIL_LENGTH);
        given(userService.signUp(email, PASSWORD)).willReturn(new UserResponse(USER_ID, email));

        postUser(body(email, PASSWORD)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("가입 형식 오류의 응답을 문서화한다")
    void documentsInvalidSignUp() throws Exception {
        postUser(body("not-an-email", "short"))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andDo(document("users-signup-invalid"));
    }

    @Test
    @DisplayName("로그인 상태면 /users/me는 200과 id, email만 응답한다 (R4, R6)")
    void getMe() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(new UserResponse(USER_ID, EMAIL));

        mvc.perform(get("/api/v1/users/me").cookie(new Cookie("access_token", TestJwts.valid(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email")))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andDo(document(
                        "users-me",
                        requestCookies(cookieWithName("access_token").description("로그인할 때 받은 인증 쿠키")),
                        responseFields(
                                fieldWithPath("id").description("사용자 ID"),
                                fieldWithPath("email").description("이메일"))));
    }

    @Test
    @DisplayName("비로그인이면 /users/me는 401 ProblemDetail이다 (R6)")
    void getMeWithoutLogin() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-unauthorized"));

        then(userService).shouldHaveNoInteractions();
    }

    private ResultActions postUser(String requestBody) throws Exception {
        return mvc.perform(
                post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(requestBody));
    }

    private static String body(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    /** @Email의 로컬 파트 한도(64자)와 도메인 라벨 한도(63자)를 지켜, 길이 외에는 형식 오류가 없는 이메일을 만든다. */
    private static String emailOfLength(int length) {
        String prefix = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".";
        String suffix = ".com";
        return prefix + "d".repeat(length - prefix.length() - suffix.length()) + suffix;
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
