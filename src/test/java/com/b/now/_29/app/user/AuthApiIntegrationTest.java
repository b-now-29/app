package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.SetCookieHeader;
import com.b.now._29.app.support.TestJwts;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 보안 필터, Service, DB를 모두 연결해 가입부터 로그아웃까지 확인한다. */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final String ME = "/api/v1/users/me";
    private static final String COOKIE_NAME = "access_token";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("가입 응답의 쿠키로 바로 /users/me를 조회할 수 있다 (R1, R6)")
    void signUpLogsIn() throws Exception {
        String email = uniqueEmail();

        MvcResult signUp = postJson("/api/v1/users", signUpBody(email, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        mvc.perform(get(ME).cookie(cookieFrom(signUp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("비밀번호는 BCrypt 해시로만 저장하고 가입·로그인·조회 응답에 해시가 없다 (R4)")
    void passwordHashIsStoredAndNeverReturned() throws Exception {
        String email = uniqueEmail();

        MvcResult signUp = postJson("/api/v1/users", signUpBody(email, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult login = postJson("/api/v1/auth/login", credentials(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult me = mvc.perform(get(ME).cookie(cookieFrom(login)))
                .andExpect(status().isOk())
                .andReturn();

        String hash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        for (MvcResult result : new MvcResult[] {signUp, login, me}) {
            assertThat(body(result))
                    .doesNotContain(hash, PASSWORD, "password", "hash")
                    .doesNotContain("$2a$", "$2b$", "$2y$");
        }
    }

    @Test
    @DisplayName("이메일은 공백을 지우고 소문자로 저장하며, 어떤 대소문자로도 로그인된다 (R2)")
    void emailIsNormalized() throws Exception {
        String email = uniqueEmail();
        String raw = "  " + email.toUpperCase(Locale.ROOT) + " ";

        postJson("/api/v1/users", signUpBody(raw, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(userRepository.existsByEmail(email)).isTrue();
        postJson("/api/v1/auth/login", credentials(raw, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("대소문자만 다른 이메일로 다시 가입하면 409다 (R2)")
    void duplicateEmailDifferingByCase() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/users", signUpBody(email, PASSWORD)).andExpect(status().isCreated());

        postJson("/api/v1/users", signUpBody(email.toUpperCase(Locale.ROOT), PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("정확히 72바이트인 한글 비밀번호로 가입하고 로그인한다 (R3)")
    void longestPasswordWorks() throws Exception {
        String email = uniqueEmail();
        String password = "가".repeat(24);

        postJson("/api/v1/users", signUpBody(email, password)).andExpect(status().isCreated());

        postJson("/api/v1/auth/login", credentials(email, password)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호의 401 응답은 본문까지 똑같다 (R5)")
    void loginFailuresAreIdentical() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/users", signUpBody(email, PASSWORD)).andExpect(status().isCreated());

        MvcResult wrongPassword = postJson("/api/v1/auth/login", credentials(email, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        // 이메일을 응답에 담으면 두 경우의 본문이 달라지므로, 같은 길이의 없는 이메일도 비교한다
        MvcResult unknownEmail = postJson("/api/v1/auth/login", credentials(uniqueEmail(), "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(body(wrongPassword)).isEqualTo(body(unknownEmail)).doesNotContain(email);
    }

    @Test
    @DisplayName("로그아웃의 Set-Cookie를 브라우저처럼 따르면 쿠키가 없어지고 /users/me는 401이다 (R7)")
    void logoutRemovesCookie() throws Exception {
        String email = uniqueEmail();
        Cookie cookie = cookieFrom(postJson("/api/v1/users", signUpBody(email, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn());

        MvcResult logout = mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();

        SetCookieHeader removal = SetCookieHeader.from(logout);
        assertThat(removal.name()).isEqualTo(COOKIE_NAME);
        assertThat(removal.attributes()).contains("Max-Age=0", "Path=/api");
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                arguments("만료", TestJwts.expired(1L)),
                arguments("다른 키로 서명", TestJwts.signedWithOtherKey(1L)),
                arguments("본문 변조", TestJwts.tampered(1L, 2L)),
                arguments("서명 없음 (alg: none)", TestJwts.unsigned(1L)),
                arguments("JWT 형식이 아님", "not-a-jwt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 쿠키로도 실제 공개 API는 정상이고 /users/me만 401이다 (R9)")
    void invalidCookieOnlyAffectsMe(String description, String token) throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, token);

        mvc.perform(get("/api/v1/categories").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories/java/questions").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/some/page").cookie(cookie)).andExpect(status().isOk());
        postJsonWithCookie("/api/v1/questions/999999/answers", "{\"choiceId\": 1}", cookie)
                .andExpect(status().isNotFound());

        mvc.perform(get(ME).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비회원은 문제를 조회하고, 답 제출도 인증·CSRF 오류 없이 채점 로직까지 도달한다 (R8, D5)")
    void guestCanUseQuizApi() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories/java/questions")).andExpect(status().isOk());

        postJson("/api/v1/questions/999999/answers", "{\"choiceId\": 1}").andExpect(status().isNotFound());
        postJson("/api/v1/questions/999999/text-answers", "{\"answer\": \"답\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("로그인 상태에서도 문제 조회 API는 정답 정보를 내려주지 않는다")
    void questionsDoNotExposeAnswersToLoggedInUser() throws Exception {
        Cookie cookie = cookieFrom(postJson("/api/v1/users", signUpBody(uniqueEmail(), PASSWORD))
                .andExpect(status().isCreated())
                .andReturn());

        mvc.perform(get("/api/v1/categories/java/questions").cookie(cookie))
                .andExpect(status().isOk())
                // 문제 본문에 같은 단어가 있어도 걸리지 않도록 값이 아니라 필드 이름을 검사한다
                .andExpect(jsonPath("$[0].keys()", containsInAnyOrder("id", "type", "content", "choices")))
                .andExpect(jsonPath("$[*].isCorrect").isEmpty())
                .andExpect(jsonPath("$[*].modelAnswer").isEmpty())
                .andExpect(jsonPath("$[*].explanation").isEmpty())
                .andExpect(jsonPath("$[*].choices[*].isCorrect").isEmpty())
                .andExpect(jsonPath("$[*].choices[*].correct").isEmpty())
                .andExpect(jsonPath("$[*].choices[*].id").isNotEmpty());
    }

    @Test
    @DisplayName("문서와 헬스 체크 경로를 인증으로 막지 않는다 (D4)")
    void docsAndActuatorArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // 테스트 환경에는 문서 파일이 없으므로 401이 아니라 404여야 한다
        mvc.perform(get("/docs/index.html")).andExpect(status().isNotFound());
    }

    // 닉네임 (nickname.md R1~R5, R7, R8)

    @Test
    @DisplayName("같은 닉네임으로 가입하면 번호가 1, 2로 늘고, 가입·로그인·내 정보 응답이 같은 닉네임과 번호를 준다 (R2, R3, R8)")
    void signUpAssignsTagsAndReturnsThem() throws Exception {
        String nickname = uniqueNickname();
        String firstEmail = uniqueEmail();
        String secondEmail = uniqueEmail();

        postJson("/api/v1/users", signUpBody(firstEmail, PASSWORD, "  " + nickname + " "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.tag").value(1));
        postJson("/api/v1/users", signUpBody(secondEmail, PASSWORD, nickname))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.tag").value(2));

        MvcResult login = postJson("/api/v1/auth/login", credentials(secondEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.tag").value(2))
                .andReturn();
        mvc.perform(get(ME).cookie(cookieFrom(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("id", "email", "role", "nickname", "tag")))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.tag").value(2));
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임은 번호를 따로 센다 (R7)")
    void nicknameIsCaseSensitive() throws Exception {
        String base = "Case" + UUID.randomUUID().toString().substring(0, 8);

        postJson("/api/v1/users", signUpBody(uniqueEmail(), PASSWORD, base))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag").value(1));
        postJson("/api/v1/users", signUpBody(uniqueEmail(), PASSWORD, base.toLowerCase(Locale.ROOT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(base.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.tag").value(1));
    }

    @Test
    @DisplayName("닉네임을 바꾸면 새 닉네임의 다음 번호를 받고 내 정보에 바로 반영되며, 같은 닉네임으로 다시 바꾸면 번호가 유지된다 (R5)")
    void changeNickname() throws Exception {
        String target = uniqueNickname();
        postJson("/api/v1/users", signUpBody(uniqueEmail(), PASSWORD, target)).andExpect(status().isCreated());
        Cookie cookie = cookieFrom(postJson("/api/v1/users", signUpBody(uniqueEmail(), PASSWORD, uniqueNickname()))
                .andExpect(status().isCreated())
                .andReturn());

        putNickname(target, cookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(target))
                .andExpect(jsonPath("$.tag").value(2));
        mvc.perform(get(ME).cookie(cookie))
                .andExpect(jsonPath("$.nickname").value(target))
                .andExpect(jsonPath("$.tag").value(2));

        putNickname(" " + target + " ", cookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(target))
                .andExpect(jsonPath("$.tag").value(2));
    }

    @Test
    @DisplayName("닉네임 없이 가입하면 400이고 회원이 생기지 않는다 (R1)")
    void signUpWithoutNickname() throws Exception {
        String email = uniqueEmail();

        postJson("/api/v1/users", credentials(email, PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("규칙에 맞지 않는 닉네임으로 바꾸면 400이고 닉네임은 그대로다 (R1, R5)")
    void changeToInvalidNickname() throws Exception {
        String nickname = uniqueNickname();
        Cookie cookie = cookieFrom(postJson("/api/v1/users", signUpBody(uniqueEmail(), PASSWORD, nickname))
                .andExpect(status().isCreated())
                .andReturn());

        putNickname("영희#1", cookie).andExpect(status().isBadRequest());

        mvc.perform(get(ME).cookie(cookie)).andExpect(jsonPath("$.nickname").value(nickname));
    }

    @Test
    @DisplayName("비회원은 닉네임을 바꿀 수 없다 (401)")
    void changeNicknameWithoutLogin() throws Exception {
        mvc.perform(put(ME + "/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"영희\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions putNickname(String nickname, Cookie cookie) throws Exception {
        return mvc.perform(put(ME + "/nickname")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"%s\"}".formatted(nickname)));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postJsonWithCookie(String path, String body, Cookie cookie) throws Exception {
        return mvc.perform(post(path)
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Cookie cookieFrom(MvcResult result) {
        SetCookieHeader header = SetCookieHeader.from(result);
        assertThat(header.name()).isEqualTo(COOKIE_NAME);
        return new Cookie(header.name(), header.value());
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private static String signUpBody(String email, String password) {
        return signUpBody(email, password, "회원가입자");
    }

    private static String signUpBody(String email, String password, String nickname) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"%s\"}".formatted(email, password, nickname);
    }

    private static String uniqueNickname() {
        return "닉" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
