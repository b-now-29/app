package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.b.now._29.app.support.ClientIps;
import com.b.now._29.app.support.MutableClock;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 가입·로그인·로그아웃·닉네임 변경·비밀번호 변경과 로그인·가입·비밀번호 변경 시도 제한을 확인한다.
 *
 * <p>번호 계산과 저장은 {@code NicknameTagAssigner}의 한 번 시도가 하므로 mock으로 두고, 재시도의 실제 트랜잭션 동작은 {@code
 * NicknameTagRetryIntegrationTest}가 확인한다. 비밀번호 저장도 {@code PasswordWriter}를 mock으로 두고, 트랜잭션 경계는 {@code
 * PasswordChangeIntegrationTest}가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String RAW_EMAIL = "  User@Example.COM ";
    private static final String PASSWORD = "password1234";
    private static final String NICKNAME = "철수";
    private static final int TAG = 1;
    private static final String EMAIL_CONSTRAINT = "users_email_key";
    private static final String TAG_CONSTRAINT = "uk_users_nickname_tag";
    // 번호도 이메일도 아닌 제약 (예: role CHECK)
    private static final String OTHER_CONSTRAINT = "users_role_check";
    private static final UserResponse CREATED_USER = new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG);
    // 가입 결과: 방금 만든 회원이라 토큰 버전은 새 회원의 버전이다
    private static final SignInResult CREATED =
            new SignInResult(CREATED_USER, new TokenSubject(USER_ID, User.INITIAL_TOKEN_VERSION));
    // 로그인 결과: savedUser()는 로그아웃한 적이 없어 지금 토큰 버전이 새 회원의 버전과 같다
    private static final SignInResult LOGGED_IN = new SignInResult(
            new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG),
            new TokenSubject(USER_ID, User.INITIAL_TOKEN_VERSION));
    private static final UserResponse RENAMED = new UserResponse(USER_ID, EMAIL, Role.USER, "영희", 2);

    // 제한 횟수는 운영값 (5·50·10)과 달라도 된다. 운영값은 통합 테스트가 확인한다. IP 한도는 쌍 한도보다 커야 둘을 구분할 수 있다
    private static final int PAIR_MAX = 3;
    private static final int IP_MAX = 5;
    private static final int SIGN_UP_MAX = 3;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration SIGN_UP_WINDOW = Duration.ofHours(1);
    // 비밀번호 변경 제한. 운영값 (회원별 15분 5회)은 AuthThrottleConfigTest와 통합 테스트가 확인한다
    private static final int PASSWORD_CHANGE_MAX = 3;
    private static final Duration PASSWORD_CHANGE_WINDOW = Duration.ofMinutes(15);
    private static final long OTHER_USER_ID = 2L;
    private static final String NEW_PASSWORD = "new-password-123";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final TokenSubject CHANGED = new TokenSubject(USER_ID, User.INITIAL_TOKEN_VERSION + 1);
    // 가입에 쓰는 것과 같은 강도 10의 BCrypt 해시: $2a$, $2b$, $2y$ 중 하나 + 강도 10 + salt·해시 53자
    private static final String COST_10_BCRYPT = "^\\$2[aby]?\\$10\\$[./A-Za-z0-9]{53}$";

    // 해시 결과를 실제로 확인하려고 진짜 인코더를 쓴다. 강도는 테스트 속도를 위해 최소로 둔다.
    // UserService에 넣는 쪽은 BCrypt 비교를 했는지, 몇 번 했는지 확인하려고 spy로 감싼다
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

    // fixture 해시와 검증용. spy를 given(...).willReturn(...) 인자 계산 중에 부르면 stubbing이 덜 끝난 상태가 되므로 따로 둔다
    private final PasswordEncoder hasher = new BCryptPasswordEncoder(4);

    // 쌍 키와 IP 키를 구분하려고 서로 다른 IP 둘을 쓴다
    private final String clientIp = ClientIps.unique();
    private final String otherIp = ClientIps.unique();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Mock
    UserRepository userRepository;

    @Mock
    NicknameTagAssigner assigner;

    @Mock
    PasswordWriter passwordWriter;

    UserService userService;

    @BeforeEach
    void setUp() {
        AuthLimiters limiters = new AuthLimiters(
                new AttemptLimiter<>(PAIR_MAX, LOGIN_WINDOW, clock),
                new AttemptLimiter<>(IP_MAX, LOGIN_WINDOW, clock),
                new AttemptLimiter<>(SIGN_UP_MAX, SIGN_UP_WINDOW, clock),
                new AttemptLimiter<>(PASSWORD_CHANGE_MAX, PASSWORD_CHANGE_WINDOW, clock));
        userService = new UserService(userRepository, passwordEncoder, assigner, passwordWriter, limiters);
    }

    // 가입 (번호 계산·저장은 NicknameTagAssigner의 한 번 시도)

    @Test
    @DisplayName("가입하면 정규화한 이메일과 BCrypt 해시로 한 번 시도를 부르고, 그 응답과 새 회원 버전의 토큰 주체를 돌려준다")
    void signUpPassesNormalizedEmailAndHash() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.USER), eq(new Nickname(NICKNAME))))
                .willReturn(CREATED_USER);

        SignInResult response = userService.signUp(RAW_EMAIL, PASSWORD, NICKNAME, clientIp);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(assigner).should().createUser(eq(EMAIL), hash.capture(), eq(Role.USER), eq(new Nickname(NICKNAME)));
        assertThat(hash.getValue()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(hasher.matches(PASSWORD, hash.getValue())).isTrue();
        assertThat(response).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("관리자·테스트 계정을 만드는 createAccount도 가입과 같은 한 번 시도로 그 role의 회원을 만든다")
    void createAccountWithRole() {
        UserResponse admin = new UserResponse(USER_ID, EMAIL, Role.ADMIN, NICKNAME, 5);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.ADMIN), eq(new Nickname(NICKNAME))))
                .willReturn(admin);

        UserResponse response = userService.createAccount(EMAIL, PASSWORD, NICKNAME, Role.ADMIN);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(assigner).should().createUser(eq(EMAIL), hash.capture(), eq(Role.ADMIN), eq(new Nickname(NICKNAME)));
        assertThat(hasher.matches(PASSWORD, hash.getValue())).isTrue();
        assertThat(response).isEqualTo(admin);
    }

    @Test
    @DisplayName("대소문자와 공백만 다른 이메일도 중복이면 저장하지 않고 DuplicateEmailException을 던진다")
    void signUpRejectsDuplicateEmail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> userService.signUp(RAW_EMAIL, PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(DuplicateEmailException.class);

        then(assigner).should(never()).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("동시 가입으로 이메일 유니크 제약을 어겨도 500이 아니라 DuplicateEmailException이고, 번호를 바꿔도 풀리지 않으므로 재시도하지 않는다")
    void signUpConvertsEmailUniqueViolation() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(violation(EMAIL_CONSTRAINT));

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(DuplicateEmailException.class);

        then(assigner).should(times(1)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("번호 제약도 이메일 제약도 아닌 제약 위반은 409로 바꾸거나 재시도하지 않고 그대로 던진다")
    void signUpRethrowsOtherConstraintViolation() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        DataIntegrityViolationException other = violation(OTHER_CONSTRAINT);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(other);

        Throwable thrown = catchThrowable(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp));

        assertThat(thrown).isSameAs(other);
        then(assigner).should(times(1)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    // 닉네임

    @Test
    @DisplayName("닉네임의 앞뒤 공백을 지운 닉네임으로 한 번 시도를 부른다")
    void signUpStripsNickname() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED_USER);

        userService.signUp(EMAIL, PASSWORD, "  " + NICKNAME + " ", clientIp);

        ArgumentCaptor<Nickname> nickname = ArgumentCaptor.forClass(Nickname.class);
        then(assigner).should().createUser(anyString(), anyString(), any(Role.class), nickname.capture());
        assertThat(nickname.getValue().value()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("닉네임은 대소문자를 그대로 둔다")
    void signUpKeepsNicknameCase() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED_USER);

        userService.signUp(EMAIL, PASSWORD, "Kim", clientIp);

        then(assigner).should().createUser(anyString(), anyString(), any(Role.class), eq(new Nickname("Kim")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "철수#1", "철\0수", "   "})
    @DisplayName("규칙에 맞지 않는 닉네임은 저장하지 않고 InvalidNicknameException이다")
    void signUpRejectsInvalidNickname(String nickname) {
        lenient().when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, nickname, clientIp))
                .isInstanceOf(InvalidNicknameException.class);

        then(assigner).shouldHaveNoInteractions();
    }

    // 번호 경합 재시도: 동시에 같은 닉네임으로 가입하면 같은 번호를 계산할 수 있다

    @Test
    @DisplayName("번호 제약을 어기면 한 번 시도를 다시 불러 두 번째 시도의 결과로 가입된다")
    void signUpRetriesOnTagConflict() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.USER), eq(new Nickname(NICKNAME))))
                .willThrow(violation(TAG_CONSTRAINT))
                .willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, 2));

        SignInResult response = userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp);

        assertThat(response)
                .isEqualTo(new SignInResult(
                        new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, 2),
                        new TokenSubject(USER_ID, User.INITIAL_TOKEN_VERSION)));
        then(assigner).should(times(2)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("번호 경합이 계속되면 몇 번 재시도한 뒤 멈추고, 이메일 중복이 아닌 예외 (500)로 끝난다")
    void signUpGivesUpAfterRepeatedTagConflicts() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(violation(TAG_CONSTRAINT));

        Throwable thrown = catchThrowable(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp));

        assertThat(thrown)
                .isNotNull()
                .isNotInstanceOf(DuplicateEmailException.class)
                .isNotInstanceOf(InvalidNicknameException.class);
        // "최대 3번"이 첫 시도를 포함한 3번인지 재시도만 3번인지 정해지지 않아 둘 다 허용한다
        then(assigner).should(atLeast(3)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
        then(assigner).should(atMost(4)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    // 로그인·조회

    @Test
    @DisplayName("정규화한 이메일로 찾아 비밀번호가 맞으면 닉네임과 번호를 포함한 사용자를 돌려준다")
    void loginWithNormalizedEmail() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        assertThat(userService.login(RAW_EMAIL, PASSWORD, clientIp).user()).isEqualTo(LOGGED_IN.user());
    }

    @Test
    @DisplayName("로그인하면 토큰을 발급할 회원 ID와 그 회원의 지금 토큰 버전을 함께 돌려준다")
    void loginReturnsCurrentTokenVersion() {
        User user = savedUser();
        user.revokeTokens();
        user.revokeTokens();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertThat(userService.login(EMAIL, PASSWORD, clientIp).tokenSubject()).isEqualTo(new TokenSubject(USER_ID, 2));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 예외와 같은 메시지라 이메일이 가입됐는지 알 수 없다")
    void loginFailuresAreIndistinguishable() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        Throwable unknownEmail = catchThrowable(() -> userService.login("nobody@example.com", PASSWORD, clientIp));
        Throwable wrongPassword = catchThrowable(() -> userService.login(EMAIL, "wrong-password", clientIp));

        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownEmail).hasMessage(wrongPassword.getMessage());
    }

    static Stream<Arguments> passwordsOverLimit() {
        return Stream.of(
                arguments("a".repeat(72), "a".repeat(73)), // 73바이트
                arguments("가".repeat(24), "가".repeat(25))); // 25자지만 75바이트
    }

    @ParameterizedTest
    @MethodSource("passwordsOverLimit")
    @DisplayName("BCrypt는 72바이트까지만 보므로, 72바이트를 넘는 비밀번호는 앞 72바이트가 저장된 비밀번호와 같아도 InvalidCredentialsException이다")
    void loginRejectsPasswordOverLimit(String storedPassword, String attempt) {
        // 구현이 조회 전에 길이로 거르든 조회 후에 거르든 같은 결과여야 하므로 조회 여부는 강제하지 않는다
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser(storedPassword)));

        assertThatThrownBy(() -> userService.login(EMAIL, attempt, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("정확히 72바이트인 비밀번호는 한도 안이므로 맞으면 로그인된다")
    void loginAcceptsPasswordAtLimit() {
        String password = "가".repeat(24);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser(password)));

        assertThat(userService.login(EMAIL, password, clientIp)).isEqualTo(LOGGED_IN);
    }

    @Test
    @DisplayName("가입 규칙보다 짧은 8자 미만 비밀번호도 로그인에서는 거르지 않고, 저장된 비밀번호와 다르면 InvalidCredentialsException이다")
    void loginWithShortPassword() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        assertThatThrownBy(() -> userService.login(EMAIL, "short", clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("이메일 형식이 아닌 값은 없는 이메일과 같이 InvalidCredentialsException이다")
    void loginWithNonEmailValue() {
        given(userRepository.findByEmail("not-an-email")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login("not-an-email", PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // 표시 이름에 NUL 문자가 들어가지 않도록 name을 번호로 둔다
    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"user\0@example.com", "\0user@example.com", "user@example.com\0"})
    @DisplayName("PostgreSQL은 NUL 문자를 거부하므로, NUL 문자가 든 이메일은 Repository를 호출하지 않고 InvalidCredentialsException이다")
    void loginWithNulInEmail(String email) {
        assertThatThrownBy(() -> userService.login(email, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("id로 사용자를 찾아 닉네임과 번호를 포함한 응답으로 돌려준다")
    void getUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(savedUser()));

        assertThat(userService.getUser(USER_ID)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG));
    }

    @Test
    @DisplayName("관리자를 조회하면 응답의 role이 ADMIN이다")
    void getAdminUser() {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(
                        withId(new User(EMAIL, hasher.encode(PASSWORD), Role.ADMIN, new Nickname(NICKNAME), TAG))));

        assertThat(userService.getUser(USER_ID)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.ADMIN, NICKNAME, TAG));
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 없으면 UserNotFoundException을 던진다")
    void getUnknownUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(USER_ID)).isInstanceOf(UserNotFoundException.class);
    }

    // 로그아웃

    @Test
    @DisplayName("로그아웃하면 그 회원의 토큰 버전이 1 오른다")
    void logoutRevokesTokensOfUser() {
        User user = savedUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        userService.logout(USER_ID);

        assertThat(user.getTokenVersion()).isEqualTo(User.INITIAL_TOKEN_VERSION + 1);
    }

    @Test
    @DisplayName("보안 필터가 확인한 뒤 회원이 사라졌을 수 있으므로, 없는 회원 ID로 로그아웃하면 예외 없이 아무것도 하지 않는다")
    void logoutOfUnknownUserDoesNothing() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatCode(() -> userService.logout(USER_ID)).doesNotThrowAnyException();

        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
        then(userRepository).should(never()).flush();
    }

    @Test
    @DisplayName("비회원의 로그아웃처럼 회원 ID가 null이면 회원을 조회하지 않고 아무것도 하지 않는다")
    void logoutWithoutUserDoesNothing() {
        assertThatCode(() -> userService.logout(null)).doesNotThrowAnyException();

        then(userRepository).shouldHaveNoInteractions();
    }

    // 닉네임 변경 (번호 계산과 엔티티 변경은 NicknameTagAssigner의 한 번 시도)

    @Test
    @DisplayName("공백을 지운 닉네임으로 한 번 시도를 부르고 그 응답을 돌려준다")
    void changeNicknameDelegatesToAttempt() {
        given(assigner.changeNickname(USER_ID, new Nickname("영희"))).willReturn(RENAMED);

        UserResponse response = userService.changeNickname(USER_ID, "  영희 ");

        assertThat(response).isEqualTo(RENAMED);
        then(assigner).should(times(1)).changeNickname(anyLong(), any(Nickname.class));
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("닉네임 변경 중 번호 제약을 어기면 한 번 시도를 다시 부른다")
    void changeNicknameRetriesOnTagConflict() {
        given(assigner.changeNickname(USER_ID, new Nickname("영희")))
                .willThrow(violation(TAG_CONSTRAINT))
                .willReturn(RENAMED);

        UserResponse response = userService.changeNickname(USER_ID, "영희");

        assertThat(response).isEqualTo(RENAMED);
        then(assigner).should(times(2)).changeNickname(anyLong(), any(Nickname.class));
    }

    @Test
    @DisplayName("닉네임 변경도 번호 경합이 계속되면 몇 번 재시도한 뒤 멈춘다")
    void changeNicknameGivesUpAfterRepeatedTagConflicts() {
        given(assigner.changeNickname(anyLong(), any(Nickname.class))).willThrow(violation(TAG_CONSTRAINT));

        Throwable thrown = catchThrowable(() -> userService.changeNickname(USER_ID, "영희"));

        assertThat(thrown).isNotNull().isNotInstanceOf(InvalidNicknameException.class);
        then(assigner).should(atLeast(3)).changeNickname(anyLong(), any(Nickname.class));
        then(assigner).should(atMost(4)).changeNickname(anyLong(), any(Nickname.class));
    }

    @Test
    @DisplayName("닉네임 변경 중 번호 제약이 아닌 제약 위반은 재시도하지 않고 그대로 던진다")
    void changeNicknameRethrowsOtherConstraintViolation() {
        DataIntegrityViolationException other = violation(OTHER_CONSTRAINT);
        given(assigner.changeNickname(USER_ID, new Nickname("영희"))).willThrow(other);

        Throwable thrown = catchThrowable(() -> userService.changeNickname(USER_ID, "영희"));

        assertThat(thrown).isSameAs(other);
        then(assigner).should(times(1)).changeNickname(anyLong(), any(Nickname.class));
    }

    @Test
    @DisplayName("규칙에 맞지 않는 닉네임은 바꾸지 않고 InvalidNicknameException이다")
    void changeNicknameRejectsInvalid() {
        assertThatThrownBy(() -> userService.changeNickname(USER_ID, "영희#2"))
                .isInstanceOf(InvalidNicknameException.class);

        then(assigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 없으면 닉네임 변경도 UserNotFoundException이고 재시도하지 않는다")
    void changeNicknameOfUnknownUser() {
        given(assigner.changeNickname(USER_ID, new Nickname("영희"))).willThrow(new UserNotFoundException(USER_ID));

        assertThatThrownBy(() -> userService.changeNickname(USER_ID, "영희")).isInstanceOf(UserNotFoundException.class);

        then(assigner).should(times(1)).changeNickname(anyLong(), any(Nickname.class));
    }

    // 로그인 시도 제한: 같은 이메일·IP 쌍과 IP를 따로 세어, 한 계정을 노리는 시도와 여러 계정을 훑는 시도를 모두 막는다

    @Test
    @DisplayName("응답 시간으로 가입 여부를 알 수 없도록, 없는 이메일도 강도 10의 더미 해시와 BCrypt 비교를 한 번 한다")
    void loginWithUnknownEmailComparesDummyHash() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login("nobody@example.com", PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(passwordEncoder).should(times(1)).matches(eq(PASSWORD), hash.capture());
        assertThat(hash.getValue()).matches(COST_10_BCRYPT);
    }

    @Test
    @DisplayName("있는 이메일은 저장된 해시와 한 번만 비교하고 더미 해시와는 비교하지 않는다")
    void loginWithKnownEmailComparesStoredHashOnce() {
        User user = savedUser();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(EMAIL, "wrong-password", clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        then(passwordEncoder).should(times(1)).matches(any(), anyString());
        then(passwordEncoder).should().matches("wrong-password", user.getPasswordHash());
    }

    @Test
    @DisplayName("같은 이메일·IP로 한도만큼 틀리면 맞는 비밀번호도 같은 401이고 BCrypt 비교를 하지 않는다")
    void blockedPairRejectsCorrectPasswordWithoutBcrypt() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);
        clearInvocations(passwordEncoder);

        Throwable thrown = catchThrowable(() -> userService.login(EMAIL, PASSWORD, clientIp));

        assertThat(thrown)
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
        then(passwordEncoder).should(never()).matches(any(), any());
    }

    @Test
    @DisplayName("대소문자와 앞뒤 공백만 다른 이메일의 실패도 같은 쌍으로 센다")
    void pairKeyUsesNormalizedEmail() {
        givenSavedUserLeniently();
        failLoginTimes(RAW_EMAIL, clientIp, PAIR_MAX - 1);
        failLoginTimes(EMAIL.toUpperCase(Locale.ROOT), clientIp, 1);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("쌍이 막혀도 다른 IP에서는 맞는 비밀번호로 로그인된다")
    void pairBlockDoesNotAffectOtherIp() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);

        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(LOGGED_IN);
    }

    @Test
    @DisplayName("쌍이 막혀도 같은 IP의 다른 이메일은 로그인된다")
    void pairBlockDoesNotAffectOtherEmail() {
        givenSavedUserLeniently();
        String otherEmail = "other@example.com";
        User other = withId(new User(otherEmail, hasher.encode(PASSWORD), new Nickname(NICKNAME), TAG));
        given(userRepository.findByEmail(otherEmail)).willReturn(Optional.of(other));
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);

        assertThat(userService.login(otherEmail, PASSWORD, clientIp).user().email())
                .isEqualTo(otherEmail);
    }

    @Test
    @DisplayName("같은 IP에서 여러 이메일로 한도만큼 틀리면 그 IP의 모든 로그인을 BCrypt 없이 거부하고, 다른 IP는 된다")
    void ipBlockRejectsAllEmailsFromThatIp() {
        givenSavedUserLeniently();
        for (int i = 0; i < IP_MAX; i++) {
            failLogin("nobody-" + i + "@example.com", clientIp);
        }
        clearInvocations(passwordEncoder);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
        then(passwordEncoder).should(never()).matches(any(), any());

        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(LOGGED_IN);
    }

    @Test
    @DisplayName("첫 실패부터 15분이 지나면 다시 로그인할 수 있다")
    void pairBlockEndsAfterWindow() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);
        clock.advance(LOGIN_WINDOW.minusSeconds(1));
        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        clock.advance(Duration.ofSeconds(2));

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(LOGGED_IN);
    }

    @Test
    @DisplayName("로그인에 성공하면 그 쌍의 실패 횟수는 지우고, IP의 실패 횟수는 남긴다")
    void successResetsPairButNotIp() {
        givenSavedUserLeniently();
        // 쌍 2, IP 2
        failLoginTimes(EMAIL, clientIp, PAIR_MAX - 1);
        userService.login(EMAIL, PASSWORD, clientIp);
        // 쌍이 지워지지 않았다면 여기서 쌍 4가 되어 막힌다. IP는 4
        failLoginTimes(EMAIL, clientIp, PAIR_MAX - 1);
        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(LOGGED_IN);

        // IP가 지워지지 않았으므로 다른 이메일로 한 번 더 틀리면 IP 5번째 실패로 막힌다
        failLogin("nobody@example.com", clientIp);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("차단된 동안의 시도는 실패 횟수에 넣지 않는다: IP는 통과했지만 쌍이 막히면 IP 예약을 되돌린다")
    void blockedAttemptsAreNotCounted() {
        givenSavedUserLeniently();
        String otherEmail = "other@example.com";
        User other = withId(new User(otherEmail, hasher.encode(PASSWORD), new Nickname(NICKNAME), TAG));
        given(userRepository.findByEmail(otherEmail)).willReturn(Optional.of(other));
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);

        // 쌍이 막힌 상태의 시도. 세었다면 IP가 한도를 넘는다
        failLoginTimes(EMAIL, clientIp, IP_MAX);

        assertThat(userService.login(otherEmail, PASSWORD, clientIp).user().email())
                .isEqualTo(otherEmail);
    }

    @Test
    @DisplayName("로그인 성공은 IP 횟수에서도 빠진다: 같은 IP에서 IP 한도보다 많이 성공해도 막히지 않는다")
    void successReleasesIpReservation() {
        givenSavedUserLeniently();
        // IP 실패 4번. 성공이 IP 몫을 되돌리지 않으면 다음 성공이 IP 5번째가 되어 그다음부터 막힌다
        for (int i = 0; i < IP_MAX - 1; i++) {
            failLogin("nobody-" + i + "@example.com", clientIp);
        }

        for (int i = 0; i < IP_MAX * 2; i++) {
            assertThat(userService.login(EMAIL, PASSWORD, clientIp))
                    .as("%d번째 성공", i + 1)
                    .isEqualTo(LOGGED_IN);
        }

        // 성공은 실패 횟수를 지우지는 않으므로, 한 번 더 틀리면 IP 한도에 닿는다
        failLogin("nobody@example.com", clientIp);
        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("비교 중인 요청이 쌍의 남은 한 번을 차지해서, 동시에 들어온 같은 이메일·IP 요청은 비교 없이 401이다")
    void inFlightLoginReservesPairSlot() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX - 1);
        AtomicReference<Throwable> concurrent = whileMatching(() -> userService.login(EMAIL, PASSWORD, clientIp));
        clearInvocations(passwordEncoder);

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(LOGGED_IN);

        assertThat(concurrent.get()).isInstanceOf(InvalidCredentialsException.class);
        then(passwordEncoder).should(times(1)).matches(any(), any());
    }

    @Test
    @DisplayName("비교 중인 요청이 IP의 남은 한 번을 차지해서, 동시에 들어온 같은 IP의 다른 이메일 요청은 비교 없이 401이다")
    void inFlightLoginReservesIpSlot() {
        givenSavedUserLeniently();
        String otherEmail = "other@example.com";
        User other = withId(new User(otherEmail, hasher.encode(PASSWORD), new Nickname(NICKNAME), TAG));
        lenient().when(userRepository.findByEmail(otherEmail)).thenReturn(Optional.of(other));
        for (int i = 0; i < IP_MAX - 1; i++) {
            failLogin("nobody-" + i + "@example.com", clientIp);
        }
        AtomicReference<Throwable> concurrent = whileMatching(() -> userService.login(otherEmail, PASSWORD, clientIp));
        clearInvocations(passwordEncoder);

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(LOGGED_IN);

        assertThat(concurrent.get()).isInstanceOf(InvalidCredentialsException.class);
        then(passwordEncoder).should(times(1)).matches(any(), any());
    }

    @Test
    @DisplayName("72바이트를 넘는 비밀번호로 거부된 로그인도 실패로 센다")
    void passwordOverLimitCountsAsFailure() {
        givenSavedUserLeniently();
        for (int i = 0; i < PAIR_MAX; i++) {
            assertThatThrownBy(() -> userService.login(EMAIL, "a".repeat(73), clientIp))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(LOGGED_IN);
    }

    @Test
    @DisplayName("NUL 문자가 든 이메일로 거부된 로그인도 실패로 센다")
    void nulEmailCountsAsFailure() {
        givenSavedUserLeniently();
        for (int i = 0; i < IP_MAX; i++) {
            failLogin("user" + i + "\0@example.com", clientIp);
        }

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(LOGGED_IN);
    }

    // 가입 시도 제한: 이메일 존재 확인과 가짜 계정 대량 생성을 함께 막으려고 IP별로 모든 시도를 센다

    @Test
    @DisplayName("같은 IP에서 성공한 가입도 시도로 세어, 한도를 넘는 시도는 아무것도 하지 않고 TooManySignUpAttemptsException이다")
    void signUpBlockedAfterLimit() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED_USER);
        for (int i = 0; i < SIGN_UP_MAX; i++) {
            userService.signUp("new-" + i + "@example.com", PASSWORD, NICKNAME, clientIp);
        }

        assertThatThrownBy(() -> userService.signUp("late@example.com", PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(TooManySignUpAttemptsException.class);

        then(userRepository).should(times(SIGN_UP_MAX)).existsByEmail(anyString());
        then(assigner)
                .should(times(SIGN_UP_MAX))
                .createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("가입 여부를 찾아보는 시도도 막도록, 이미 가입한 이메일 (409)로 거부된 가입도 시도로 센다")
    void duplicateEmailSignUpCounts() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        for (int i = 0; i < SIGN_UP_MAX; i++) {
            assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp))
                    .isInstanceOf(DuplicateEmailException.class);
        }

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(TooManySignUpAttemptsException.class);
    }

    @Test
    @DisplayName("가입이 막혀도 다른 IP는 가입할 수 있고, 첫 시도부터 1시간이 지나면 풀린다")
    void signUpBlockIsPerIpAndEndsAfterWindow() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED_USER);
        for (int i = 0; i < SIGN_UP_MAX; i++) {
            userService.signUp("new-" + i + "@example.com", PASSWORD, NICKNAME, clientIp);
        }

        assertThat(userService.signUp("other-ip@example.com", PASSWORD, NICKNAME, otherIp))
                .isEqualTo(CREATED);

        clock.advance(SIGN_UP_WINDOW.minusSeconds(1));
        assertThatThrownBy(() -> userService.signUp("late@example.com", PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(TooManySignUpAttemptsException.class);

        clock.advance(Duration.ofSeconds(2));
        assertThat(userService.signUp("later@example.com", PASSWORD, NICKNAME, clientIp))
                .isEqualTo(CREATED);
    }

    @Test
    @DisplayName("Retry-After 헤더에 쓰도록, 가입 차단 예외는 그 IP 창이 끝날 때까지 남은 시간을 담는다")
    void signUpBlockCarriesRetryAfter() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED_USER);
        for (int i = 0; i < SIGN_UP_MAX; i++) {
            userService.signUp("new-" + i + "@example.com", PASSWORD, NICKNAME, clientIp);
        }
        clock.advance(Duration.ofMinutes(10));

        Throwable thrown = catchThrowable(() -> userService.signUp("late@example.com", PASSWORD, NICKNAME, clientIp));

        assertThat(thrown).isInstanceOf(TooManySignUpAttemptsException.class);
        assertThat(((TooManySignUpAttemptsException) thrown).retryAfter())
                .isEqualTo(SIGN_UP_WINDOW.minus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("관리자·테스트 계정을 만드는 createAccount는 가입 제한을 받지 않는다")
    void createAccountIsNotLimited() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED_USER);

        for (int i = 0; i < SIGN_UP_MAX * 2; i++) {
            assertThat(userService.createAccount("seed-" + i + "@example.com", PASSWORD, NICKNAME, Role.USER))
                    .isEqualTo(CREATED_USER);
        }

        then(assigner)
                .should(times(SIGN_UP_MAX * 2))
                .createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("TooManySignUpAttemptsException의 메시지는 API 계약의 429 detail 문구다")
    void tooManySignUpAttemptsMessage() {
        TooManySignUpAttemptsException exception = new TooManySignUpAttemptsException(Duration.ofMinutes(5));

        assertThat(exception).hasMessage("가입 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.");
        assertThat(exception.retryAfter()).isEqualTo(Duration.ofMinutes(5));
    }

    // 비밀번호 변경 (docs/features/password-change.md). 저장은 PasswordWriter의 한 번 쓰기가 하므로 mock으로 둔다

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호의 BCrypt 해시와 비교에 쓴 해시로 저장을 부르고, 저장이 돌려준 새 토큰 주체를 돌려준다 (R1, R5, D3, D5)")
    void changePasswordHashesNewPasswordAndDelegatesSave() {
        User user = savedUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordWriter.changePassword(eq(USER_ID), eq(user.getPasswordHash()), anyString()))
                .willReturn(CHANGED);

        TokenSubject result = userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD);

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        then(passwordWriter).should().changePassword(eq(USER_ID), eq(user.getPasswordHash()), newHash.capture());
        assertThat(newHash.getValue()).startsWith("$2").isNotEqualTo(NEW_PASSWORD);
        assertThat(hasher.matches(NEW_PASSWORD, newHash.getValue())).isTrue();
        assertThat(result).isEqualTo(CHANGED);
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("현재 비밀번호는 저장된 해시와 한 번만 비교한다")
    void changePasswordComparesStoredHashOnce() {
        User user = savedUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willReturn(CHANGED);

        userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD);

        then(passwordEncoder).should(times(1)).matches(any(), anyString());
        then(passwordEncoder).should().matches(PASSWORD, user.getPasswordHash());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 저장하지 않고 \"현재 비밀번호가 맞지 않습니다.\" 예외다 (R2)")
    void changePasswordRejectsWrongCurrentPassword() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(savedUser()));

        assertThatThrownBy(() -> userService.changePassword(USER_ID, WRONG_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(WrongCurrentPasswordException.class)
                .hasMessage("현재 비밀번호가 맞지 않습니다.");

        then(passwordWriter).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("passwordsOverLimit")
    @DisplayName("72바이트를 넘는 현재 비밀번호는 앞 72바이트가 맞아도 BCrypt를 부르지 않고 R2와 같은 예외다 (D6)")
    void changePasswordRejectsCurrentPasswordOverLimitWithoutBcrypt(String storedPassword, String attempt) {
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(savedUser(storedPassword)));

        assertThatThrownBy(() -> userService.changePassword(USER_ID, attempt, NEW_PASSWORD))
                .isInstanceOf(WrongCurrentPasswordException.class)
                .hasMessage("현재 비밀번호가 맞지 않습니다.");

        then(passwordEncoder).should(never()).matches(any(), any());
        then(passwordWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정확히 72바이트인 현재 비밀번호는 한도 안이므로 맞으면 바꾼다 (D6)")
    void changePasswordAcceptsCurrentPasswordAtLimit() {
        String current = "가".repeat(24);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(savedUser(current)));
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willReturn(CHANGED);

        assertThat(userService.changePassword(USER_ID, current, NEW_PASSWORD)).isEqualTo(CHANGED);
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 같아도 바꾼다 (D7)")
    void changePasswordAllowsSamePassword() {
        User user = savedUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(passwordWriter.changePassword(eq(USER_ID), eq(user.getPasswordHash()), anyString()))
                .willReturn(CHANGED);

        assertThat(userService.changePassword(USER_ID, PASSWORD, PASSWORD)).isEqualTo(CHANGED);

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        then(passwordWriter).should().changePassword(eq(USER_ID), anyString(), newHash.capture());
        assertThat(hasher.matches(PASSWORD, newHash.getValue())).isTrue();
    }

    @Test
    @DisplayName("토큰은 유효하지만 회원이 없으면 UserNotFoundException이고 저장하지 않는다")
    void changePasswordOfUnknownUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(UserNotFoundException.class);

        then(passwordWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비교 뒤 저장 단계에서 다른 요청이 비밀번호를 바꾼 것이 드러나면 그 R2 예외를 그대로 던진다 (D5)")
    void changePasswordPropagatesConcurrentChange() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(savedUser()));
        WrongCurrentPasswordException concurrent = new WrongCurrentPasswordException();
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willThrow(concurrent);

        Throwable thrown = catchThrowable(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD));

        assertThat(thrown).isSameAs(concurrent);
    }

    // 비밀번호 변경 시도 제한 (R7, D2)

    @Test
    @DisplayName("같은 회원이 한도만큼 틀리면 맞는 현재 비밀번호도 BCrypt 비교 없이 TooManyPasswordChangeAttemptsException이고 저장하지 않는다 (R7)")
    void changePasswordBlockedAfterLimitWithoutBcrypt() {
        givenSavedUserByIdLeniently();
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX);
        clearInvocations(passwordEncoder);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(TooManyPasswordChangeAttemptsException.class);

        then(passwordEncoder).should(never()).matches(any(), any());
        then(passwordWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Retry-After 헤더에 쓰도록, 차단 예외는 그 회원의 창이 끝날 때까지 남은 시간을 담는다 (R7)")
    void changePasswordBlockCarriesRetryAfter() {
        givenSavedUserByIdLeniently();
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX);
        clock.advance(Duration.ofMinutes(10));

        Throwable thrown = catchThrowable(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD));

        assertThat(thrown).isInstanceOf(TooManyPasswordChangeAttemptsException.class);
        assertThat(((TooManyPasswordChangeAttemptsException) thrown).retryAfter())
                .isEqualTo(PASSWORD_CHANGE_WINDOW.minus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("시도 횟수는 회원별이라, 한 회원이 막혀도 다른 회원은 바꿀 수 있다 (D2)")
    void changePasswordLimitIsPerUser() {
        givenSavedUserByIdLeniently();
        User other = withId(
                new User("other@example.com", hasher.encode(PASSWORD), new Nickname(NICKNAME), TAG), OTHER_USER_ID);
        given(userRepository.findById(OTHER_USER_ID)).willReturn(Optional.of(other));
        TokenSubject otherChanged = new TokenSubject(OTHER_USER_ID, User.INITIAL_TOKEN_VERSION + 1);
        given(passwordWriter.changePassword(eq(OTHER_USER_ID), anyString(), anyString()))
                .willReturn(otherChanged);
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX);

        assertThat(userService.changePassword(OTHER_USER_ID, PASSWORD, NEW_PASSWORD))
                .isEqualTo(otherChanged);
    }

    @Test
    @DisplayName("72바이트를 넘어 BCrypt 없이 거부된 시도도 횟수에 센다 (D6)")
    void currentPasswordOverLimitCountsAsAttempt() {
        givenSavedUserByIdLeniently();
        for (int i = 0; i < PASSWORD_CHANGE_MAX; i++) {
            assertThatThrownBy(() -> userService.changePassword(USER_ID, "a".repeat(73), NEW_PASSWORD))
                    .isInstanceOf(WrongCurrentPasswordException.class);
        }

        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(TooManyPasswordChangeAttemptsException.class);
    }

    @Test
    @DisplayName("저장까지 성공하면 그 회원의 횟수를 지운다 (R7, D12)")
    void successfulChangeResetsAttempts() {
        givenSavedUserByIdLeniently();
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willReturn(CHANGED);
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX - 1);
        userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD);
        // 지워지지 않았다면 여기서 한도를 넘는다
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX - 1);

        assertThat(userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD)).isEqualTo(CHANGED);
        then(passwordWriter).should(times(2)).changePassword(eq(USER_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("비교는 맞았지만 저장 단계에서 다른 요청의 변경이 드러나 400이면 실패로 남겨, 횟수를 지우지 않는다 (D5, D12)")
    void concurrentChangeDoesNotResetAttempts() {
        givenSavedUserByIdLeniently();
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willThrow(new WrongCurrentPasswordException());
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX - 1);

        // 한도의 마지막 시도: 비교는 맞지만 저장이 R2 예외로 끝난다
        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(WrongCurrentPasswordException.class);
        clearInvocations(passwordEncoder, passwordWriter);

        // 지워졌다면 다시 비교하고 저장을 부른다
        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(TooManyPasswordChangeAttemptsException.class);
        then(passwordEncoder).should(never()).matches(any(), any());
        then(passwordWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("저장 단계에서 회원이 없어져 실패해도 횟수를 지우지 않는다 (D12)")
    void failedSaveDoesNotResetAttempts() {
        givenSavedUserByIdLeniently();
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willThrow(new UserNotFoundException(USER_ID));
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX - 1);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(UserNotFoundException.class);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(TooManyPasswordChangeAttemptsException.class);
    }

    @Test
    @DisplayName("첫 시도부터 15분이 지나면 다시 바꿀 수 있다 (R7)")
    void changePasswordBlockEndsAfterWindow() {
        givenSavedUserByIdLeniently();
        given(passwordWriter.changePassword(eq(USER_ID), anyString(), anyString()))
                .willReturn(CHANGED);
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX);
        clock.advance(PASSWORD_CHANGE_WINDOW.minusSeconds(1));
        assertThatThrownBy(() -> userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD))
                .isInstanceOf(TooManyPasswordChangeAttemptsException.class);

        clock.advance(Duration.ofSeconds(2));

        assertThat(userService.changePassword(USER_ID, PASSWORD, NEW_PASSWORD)).isEqualTo(CHANGED);
    }

    @Test
    @DisplayName("비밀번호 변경 제한은 로그인 제한과 따로 센다: 비밀번호 변경이 막혀도 로그인은 된다")
    void passwordChangeLimitDoesNotAffectLogin() {
        givenSavedUserByIdLeniently();
        givenSavedUserLeniently();
        failPasswordChangeTimes(USER_ID, PASSWORD_CHANGE_MAX);

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(LOGGED_IN);
    }

    /** 제한에 걸린 변경은 Repository 조회를 생략해도 되므로 조회 여부를 강제하지 않는다. */
    private void givenSavedUserByIdLeniently() {
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(savedUser()));
    }

    private void failPasswordChangeTimes(long userId, int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> userService.changePassword(userId, WRONG_PASSWORD, NEW_PASSWORD))
                    .isInstanceOf(WrongCurrentPasswordException.class);
        }
    }

    /**
     * spy 인코더의 첫 {@code matches}가 실행되는 동안 (다른 요청이 비교 중인 순간) {@code concurrentLogin}을 한 번 부르고, 그 결과
     * 예외를 돌려준다. 예외 없이 끝나면 null이다.
     */
    private AtomicReference<Throwable> whileMatching(ThrowingCallable concurrentLogin) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        willAnswer(invocation -> {
                    if (called.compareAndSet(false, true)) {
                        thrown.set(catchThrowable(concurrentLogin));
                    }
                    return invocation.callRealMethod();
                })
                .given(passwordEncoder)
                .matches(any(), any());
        return thrown;
    }

    /** 제한에 걸린 로그인은 Repository 조회를 생략해도 되므로 조회 여부를 강제하지 않는다. */
    private void givenSavedUserLeniently() {
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser()));
    }

    private void failLoginTimes(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            failLogin(email, ip);
        }
    }

    private void failLogin(String email, String ip) {
        assertThatThrownBy(() -> userService.login(email, "wrong-password", ip))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private User savedUser() {
        return savedUser(PASSWORD);
    }

    private User savedUser(String password) {
        return withId(new User(EMAIL, hasher.encode(password), new Nickname(NICKNAME), TAG));
    }

    private static User withId(User user) {
        return withId(user, USER_ID);
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** Hibernate가 PostgreSQL 유니크 제약 위반을 번역한 모양. 제약 이름은 원인 예외와 메시지에 모두 담긴다. */
    private static DataIntegrityViolationException violation(String constraint) {
        String message = "ERROR: duplicate key value violates unique constraint \"" + constraint + "\"";
        SQLException sqlException = new SQLException(message, "23505");
        return new DataIntegrityViolationException(
                "could not execute statement [" + message + "]; constraint [" + constraint + "]",
                new ConstraintViolationException("could not execute statement", sqlException, constraint));
    }
}
