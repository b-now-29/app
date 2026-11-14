package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
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
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.support.ClientIps;
import com.b.now._29.app.support.SetCookieHeader;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 닉네임 (docs/features/nickname.md R1, R8, API 계약)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code SignUpRequest(String email, String password, String nickname)} — nickname 필수, 검증 전 strip, 2~20 code point,
 *       {@code #}·제어 문자 금지 ({@code Nickname} 규칙과 같게. 커스텀 제약 등). 컨트롤러는 {@code userService.signUp(email, password,
 *       nickname)}
 *   <li>{@code NicknameChangeRequest(String nickname)} — 같은 검증
 *   <li>{@code PUT /api/v1/users/me/nickname} → {@code userService.changeNickname(userId, nickname)}, 200 {@code
 *       UserResponse}. 인증은 기존 {@code /api/v1/users/me/**} 규칙
 *   <li>{@code ApiExceptionHandler}: {@code InvalidNicknameException(String message)} → 400
 * </ul>
 */
@WebMvcTest(UserController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class UserControllerTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password1234";
    private static final String NICKNAME = "철수";
    private static final int TAG = 3;
    private static final String HANGUL_SYLLABLE = "가"; // UTF-8 3바이트
    private static final String MATH_A = "𝒜"; // code point 1개, UTF-16 2글자
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final String NICKNAME_PATH = "/api/v1/users/me/nickname";

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenService tokenService;

    @MockitoBean
    UserService userService;

    private final String clientIp = ClientIps.unique();

    @Test
    @DisplayName("가입하면 201, 닉네임과 번호를 포함한 UserResponse, 바로 로그인되는 쿠키를 응답한다 (R1, nickname R1, R8)")
    void signUp() throws Exception {
        given(userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp)).willReturn(response(Role.USER));

        MvcResult result = postUser(body(EMAIL, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email", "role", "nickname", "tag")))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.tag").value(TAG))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andDo(document(
                        "users-signup",
                        requestFields(
                                fieldWithPath("email").description("이메일. 앞뒤 공백을 지우고 소문자로 저장한다"),
                                fieldWithPath("password").description("비밀번호. 8자 이상, UTF-8 72바이트 이하"),
                                fieldWithPath("nickname")
                                        .description(
                                                "닉네임. 앞뒤 공백을 지운 뒤 2~20자, 글자·숫자·공백·`_` `-` `.`만 쓸 수 있다. 다른 회원과 같아도 된다")),
                        responseHeaders(headerWithName(HttpHeaders.SET_COOKIE).description("인증 쿠키 access_token")),
                        responseFields(userFields("정규화된 이메일", "권한. 새로 가입하면 USER"))))
                .andReturn();

        SetCookieHeader cookie = SetCookieHeader.from(result);
        assertThat(cookie.name()).isEqualTo("access_token");
        assertThat(cookie.attributes()).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=604800");
        assertThat(tokenService.parseUserId(cookie.value())).contains(USER_ID);
    }

    @Test
    @DisplayName("앞뒤 공백과 대문자가 있는 이메일은 형식 오류가 아니다 (R2)")
    void signUpAcceptsUnnormalizedEmail() throws Exception {
        given(userService.signUp(anyString(), eq(PASSWORD), anyString(), eq(clientIp)))
                .willReturn(response(Role.USER));

        postUser(body("  User@Example.COM ", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("중복 이메일은 409 ProblemDetail이고 쿠키를 주지 않는다 (R2)")
    void signUpWithDuplicateEmail() throws Exception {
        DuplicateEmailException exception = new DuplicateEmailException(EMAIL);
        given(userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp)).willThrow(exception);

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
        given(userService.signUp(EMAIL, password, NICKNAME, clientIp)).willReturn(response(Role.USER));

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
                "{\"email\": \"user@example.com\", \"nickname\": \"철수\"}",
                "{\"password\": \"password1234\", \"nickname\": \"철수\"}",
                "{\"email\": null, \"password\": \"password1234\", \"nickname\": \"철수\"}",
                "{\"email\": \"user@example.com\", \"password\": null, \"nickname\": \"철수\"}",
                "{\"email\": \"\", \"password\": \"password1234\", \"nickname\": \"철수\"}",
                "{\"email\": \"   \", \"password\": \"password1234\", \"nickname\": \"철수\"}",
                "{\"email\": \"not-an-email\", \"password\": \"password1234\", \"nickname\": \"철수\"}",
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
        given(userService.signUp(email, PASSWORD, NICKNAME, clientIp))
                .willReturn(new UserResponse(USER_ID, email, Role.USER, NICKNAME, TAG));

        postUser(body(email, PASSWORD)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("가입 형식 오류의 응답을 문서화한다")
    void documentsInvalidSignUp() throws Exception {
        postUser(body("not-an-email", "short"))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andDo(document("users-signup-invalid"));
    }

    // 가입 닉네임 검증 (nickname R1, D7)

    static Stream<Arguments> validNicknames() {
        return Stream.of(
                arguments("한글 2자", "철수"),
                arguments("영문 20자", "a".repeat(20)),
                arguments("앞뒤 공백을 지우면 2자", "  ab  "),
                arguments("보조 평면 문자 11개: UTF-16 22글자지만 11자", MATH_A.repeat(11)),
                arguments("보조 평면 문자 20개", MATH_A.repeat(20)),
                arguments("한글·영문·숫자·공백·_ - . (D10)", "Kim 철수_99-a.b"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validNicknames")
    @DisplayName("규칙에 맞는 닉네임은 가입할 수 있다 (nickname R1, D7)")
    void signUpWithValidNickname(String description, String nickname) throws Exception {
        given(userService.signUp(eq(EMAIL), eq(PASSWORD), anyString(), eq(clientIp)))
                .willReturn(response(Role.USER));

        postUser(body(EMAIL, PASSWORD, json(nickname))).andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidNicknames() {
        return Stream.of(
                arguments("누락", null),
                arguments("null", "null"),
                arguments("빈 문자열", json("")),
                arguments("공백뿐", json("   ")),
                arguments("1자", json("a")),
                arguments("지운 뒤 1자", json("  a  ")),
                arguments("21자", json("a".repeat(21))),
                arguments("보조 평면 문자 1개: UTF-16 2글자지만 1자", json(MATH_A)),
                arguments("보조 평면 문자 21개", json(MATH_A.repeat(21))),
                arguments("# 포함", json("철수#1")),
                arguments("NUL", "\"철\\u0000수\""),
                arguments("NUL 끝", "\"철수\\u0000\""),
                arguments("가운데 탭", "\"철\\t수\""),
                arguments("가운데 줄바꿈", "\"철\\n수\""),
                arguments("DEL", "\"철\\u007F수\""),
                // 보이지 않는 문자·짝 안 맞는 surrogate (제약 사항, 코드 리뷰)
                arguments("U+200B 폭 없는 공백", "\"철\\u200B수\""),
                arguments("U+FEFF BOM", "\"\\uFEFF철수\""),
                arguments("짝 없는 surrogate", "\"\\uD800\\uD800\""),
                arguments("짝 없는 low surrogate", "\"철\\uDC00수\""),
                arguments("한글 채움 문자 U+3164", "\"철\\u3164수\""),
                arguments("한글 채움 문자 U+115F·U+1160", "\"\\u115F\\u1160\""),
                arguments("반각 한글 채움 U+FFA0", "\"철\\uFFA0수\""),
                // 허용 목록 밖의 기호·공백·이모지 (D10)
                arguments("느낌표", json("kim_99!")),
                arguments("@", json("kim@home")),
                arguments("전각 공백 U+3000", "\"철\\u3000수\""),
                arguments("이모지", "\"철수\\uD83D\\uDE00\""),
                arguments("ZWJ 조합 이모지", "\"철수\\uD83D\\uDC68\\u200D\\uD83D\\uDC69\""),
                arguments("피부색 이모지", "\"철수\\uD83D\\uDC4D\\uD83C\\uDFFD\""),
                arguments("국기 이모지", "\"철수\\uD83C\\uDDF0\\uD83C\\uDDF7\""),
                arguments("이모지 선택자 U+FE0F", "\"철수\\u2764\\uFE0F\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNicknames")
    @DisplayName("닉네임이 없거나 규칙을 어기면 400 ProblemDetail이고 Service를 호출하지 않는다 (nickname R1, D7)")
    void signUpWithInvalidNickname(String description, String nicknameJson) throws Exception {
        postUser(body(EMAIL, PASSWORD, nicknameJson))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail", anyOf(containsString("nickname"), containsString("닉네임"))))
                .andExpect(jsonPath("$.detail", not(containsString("이메일"))))
                .andExpect(jsonPath("$.detail", not(containsString("비밀번호"))));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("닉네임 규칙 위반의 detail에 입력한 닉네임을 담지 않는다 (nickname R1, D14)")
    void signUpInvalidNicknameDoesNotEchoInput() throws Exception {
        postUser(body(EMAIL, PASSWORD, json("철수#1234")))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(content().string(not(containsString("철수#1234"))))
                .andDo(document("users-signup-invalid-nickname"));
    }

    // 내 정보

    @Test
    @DisplayName("로그인 상태면 /users/me는 200과 id, email, role, nickname, tag를 응답한다 (R4, R6, 관리자 R3, nickname R8)")
    void getMe() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(response(Role.ADMIN));

        mvc.perform(get("/api/v1/users/me").cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email", "role", "nickname", "tag")))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.tag").value(TAG))
                .andDo(document(
                        "users-me",
                        requestCookies(cookieWithName("access_token").description("로그인할 때 받은 인증 쿠키")),
                        responseFields(userFields("이메일", "권한 (USER, ADMIN). ADMIN이면 관리 화면을 쓸 수 있다"))));
    }

    @Test
    @DisplayName("일반 회원의 /users/me는 role USER를 응답한다 (R1, R3)")
    void getMeOfUser() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(response(Role.USER));

        mvc.perform(get("/api/v1/users/me").cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("비로그인이면 /users/me는 401 ProblemDetail이다 (R6)")
    void getMeWithoutLogin() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-unauthorized"));

        then(userService).shouldHaveNoInteractions();
    }

    // 닉네임 변경 (nickname R5, API 계약)

    @Test
    @DisplayName("닉네임을 바꾸면 200과 바뀐 내 정보를 가입 응답과 같은 모양으로 응답한다 (nickname R5)")
    void changeNickname() throws Exception {
        given(userService.changeNickname(USER_ID, "영희"))
                .willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "영희", 2));

        putNickname("{\"nickname\": \"영희\"}", loginCookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email", "role", "nickname", "tag")))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.nickname").value("영희"))
                .andExpect(jsonPath("$.tag").value(2))
                .andDo(document(
                        "users-me-nickname-change",
                        requestCookies(cookieWithName("access_token").description("로그인할 때 받은 인증 쿠키")),
                        requestFields(fieldWithPath("nickname")
                                .description("새 닉네임. 가입과 같은 규칙. 지금과 같으면 번호를 유지하고, 다르면 새 닉네임의 번호를 새로 받는다")),
                        responseFields(userFields("이메일", "권한 (USER, ADMIN)"))));

        then(userService).should().changeNickname(USER_ID, "영희");
    }

    @Test
    @DisplayName("앞뒤 공백이 있는 닉네임은 형식 오류가 아니다 (nickname R1)")
    void changeNicknameAcceptsSurroundingSpaces() throws Exception {
        given(userService.changeNickname(eq(USER_ID), anyString()))
                .willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, "영희", 2));

        putNickname("{\"nickname\": \"  영희 \"}", loginCookie()).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNicknames")
    @DisplayName("닉네임이 없거나 규칙을 어기면 변경도 400이고 Service를 호출하지 않는다 (nickname R1, API 계약)")
    void changeNicknameWithInvalidNickname(String description, String nicknameJson) throws Exception {
        String requestBody = nicknameJson == null ? "{}" : "{\"nickname\": " + nicknameJson + "}";

        putNickname(requestBody, loginCookie()).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(userService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nickname\": ", ""})
    @DisplayName("깨진 JSON이나 빈 본문의 닉네임 변경은 400이다")
    void changeNicknameWithUnreadableBody(String requestBody) throws Exception {
        putNickname(requestBody, loginCookie()).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("닉네임 변경 형식 오류의 응답을 문서화한다")
    void documentsInvalidChangeNickname() throws Exception {
        putNickname("{\"nickname\": \"영희#1\"}", loginCookie())
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(content().string(not(containsString("영희#1"))))
                .andDo(document("users-me-nickname-change-invalid"));
    }

    @Test
    @DisplayName("Service가 InvalidNicknameException을 던져도 400 ProblemDetail이다 (nickname R1)")
    void changeNicknameServiceRejection() throws Exception {
        given(userService.changeNickname(USER_ID, "영희")).willThrow(new InvalidNicknameException("닉네임이 올바르지 않습니다."));

        putNickname("{\"nickname\": \"영희\"}", loginCookie()).andExpect(problemDetail(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("비로그인이면 닉네임 변경은 401 ProblemDetail이고 Service를 호출하지 않는다 (nickname API 계약)")
    void changeNicknameWithoutLogin() throws Exception {
        putNickname("{\"nickname\": \"영희\"}", null)
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED))
                .andDo(document("users-me-nickname-change-unauthorized"));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만료된 쿠키로 닉네임을 바꾸면 401이다 (nickname API 계약)")
    void changeNicknameWithExpiredCookie() throws Exception {
        putNickname("{\"nickname\": \"영희\"}", new Cookie("access_token", TestJwts.expired(USER_ID)))
                .andExpect(problemDetail(HttpStatus.UNAUTHORIZED));

        then(userService).shouldHaveNoInteractions();
    }

    // 가입 시도 제한 (login-hardening R9, D5, D12, API 계약)

    @Test
    @DisplayName("요청의 remoteAddr를 클라이언트 IP로 Service에 넘긴다 (D12)")
    void signUpPassesRemoteAddrToService() throws Exception {
        String ip = ClientIps.unique();
        given(userService.signUp(EMAIL, PASSWORD, NICKNAME, ip)).willReturn(response(Role.USER));

        mvc.perform(post("/api/v1/users")
                        .with(ClientIps.from(ip))
                        .header("X-Forwarded-For", ClientIps.unique())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());

        then(userService).should().signUp(EMAIL, PASSWORD, NICKNAME, ip);
    }

    @Test
    @DisplayName("가입 시도가 너무 많으면 429 ProblemDetail과 남은 초를 올림한 Retry-After를 주고 쿠키는 주지 않는다 (R9, R15, D5)")
    void signUpWithTooManyAttempts() throws Exception {
        // 42분 0.3초 남음 → 2521초로 올림
        given(userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp))
                .willThrow(new TooManySignUpAttemptsException(
                        Duration.ofMinutes(42).plusMillis(300)));

        MvcResult result = postUser(body(EMAIL, PASSWORD))
                .andExpect(problemDetail(HttpStatus.TOO_MANY_REQUESTS))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2521"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("가입 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요."))
                .andExpect(jsonPath("$.instance").value("/api/v1/users"))
                .andDo(document(
                        "users-signup-too-many-attempts",
                        responseHeaders(
                                headerWithName(HttpHeaders.RETRY_AFTER).description("다시 가입을 시도할 수 있을 때까지 남은 시간 (초)"))))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    static Stream<Arguments> retryAfterSeconds() {
        return Stream.of(
                arguments(Duration.ofSeconds(30), "30"),
                arguments(Duration.ofSeconds(30).plusNanos(1), "31"), // 올림
                arguments(Duration.ofMillis(1), "1"), // 1초 미만도 올림
                arguments(Duration.ZERO, "1")); // 최소 1
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("retryAfterSeconds")
    @DisplayName("Retry-After는 남은 시간을 초 단위로 올림하고 최소 1이다 (R15)")
    void retryAfterIsCeilingSecondsAtLeastOne(Duration retryAfter, String expected) throws Exception {
        given(userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp))
                .willThrow(new TooManySignUpAttemptsException(retryAfter));

        postUser(body(EMAIL, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, expected));
    }

    private ResultActions postUser(String requestBody) throws Exception {
        return mvc.perform(post("/api/v1/users")
                .with(ClientIps.from(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private ResultActions putNickname(String requestBody, Cookie cookie) throws Exception {
        var request = put(NICKNAME_PATH).contentType(MediaType.APPLICATION_JSON).content(requestBody);
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mvc.perform(request);
    }

    private static Cookie loginCookie() {
        return new Cookie("access_token", TestJwts.valid(USER_ID));
    }

    private static UserResponse response(Role role) {
        return new UserResponse(USER_ID, EMAIL, role, NICKNAME, TAG);
    }

    private static FieldDescriptor[] userFields(String emailDescription, String roleDescription) {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("사용자 ID"),
            fieldWithPath("email").description(emailDescription),
            fieldWithPath("role").description(roleDescription),
            fieldWithPath("nickname").description("닉네임"),
            fieldWithPath("tag").description("같은 닉네임 안에서 회원을 구분하는 번호 (1부터). 화면에는 `닉네임#번호`로 표시한다")
        };
    }

    private static String body(String email, String password) {
        return body(email, password, json(NICKNAME));
    }

    /** nicknameJson이 null이면 nickname 필드를 넣지 않는다. 그 밖에는 JSON 값 그대로 넣는다. */
    private static String body(String email, String password, String nicknameJson) {
        if (nicknameJson == null) {
            return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
        }
        return "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": %s}".formatted(email, password, nicknameJson);
    }

    private static String json(String value) {
        return "\"" + value + "\"";
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
