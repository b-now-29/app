package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 닉네임 (docs/features/nickname.md R1~R5, D3, D4, D12, 제약 사항)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code UserService.signUp(String email, String password, String nickname): UserResponse} — 닉네임 검증, 이메일 정규화·사전
 *       중복 검사, 비밀번호 해시 뒤 {@code NicknameTagAssigner.createUser}를 부른다. {@code uk_users_nickname_tag} 위반이면 다시
 *       부르고 (재시도, D4), 이메일 제약 ({@code users_email_key}) 위반은 재시도하지 않고 {@code DuplicateEmailException}, 그 밖은
 *       그대로 던진다
 *   <li>{@code UserService.changeNickname(long userId, String nickname): UserResponse} — 닉네임 검증 뒤 {@code
 *       NicknameTagAssigner.changeNickname}을 부르고 같은 방식으로 재시도한다. 없는 회원은 {@code UserNotFoundException}
 *   <li>(D12) 생성자 {@code UserService(UserRepository, PasswordEncoder, NicknameTagAssigner)}. 번호 계산과 저장은 {@code
 *       NicknameTagAssigner} (한 번 시도, 자기 트랜잭션)가 하고, 가입·변경의 재시도 반복은 private 헬퍼 하나로 모은다. 재시도는 트랜잭션
 *       밖에서 해야 하므로 실제 트랜잭션 동작은 {@code NicknameTagRetryIntegrationTest}가 확인한다. 번호 계산 자체는 {@code
 *       NicknameTagAssignerTest}, {@code NicknameTagTest}가 확인한다
 *   <li>{@code UserResponse(long id, String email, Role role, String nickname, int tag)}
 * </ul>
 *
 * <p>로그인 보안 강화 (docs/features/login-hardening.md R1~R10, D1, D3~D11)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>생성자 {@code UserService(UserRepository, PasswordEncoder, NicknameTagAssigner, AuthLimiters)}
 *   <li>{@code record AuthLimiters(AttemptLimiter loginPair, AttemptLimiter loginIp, AttemptLimiter signUpIp)}
 *   <li>{@code login(String email, String password, String clientIp)} — 쌍 키 {@code normalizeEmail(email) + "|" + ip}, IP
 *       키 {@code ip} (D11). 둘 중 하나라도 막혔으면 BCrypt 없이 {@code InvalidCredentialsException}이고 기록하지 않는다 (R3, R5).
 *       실패 (72바이트 초과·NUL 포함)는 두 limiter에 기록하고 (R6), 성공하면 쌍만 reset (R4). 없는 이메일도 강도 10 더미 해시와
 *       {@code matches}를 한 번 부른다 (R7)
 *   <li>{@code signUp(String email, String password, String nickname, String clientIp)} — 막혔으면 {@code
 *       TooManySignUpAttemptsException}, 아니면 성공·실패와 상관없이 기록한다 (R9). {@code signUp(..., Role)}은 제한 없음 (R10)
 *   <li>{@code TooManySignUpAttemptsException()} — 메시지 "가입 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요."
 * </ul>
 *
 * <p>코드 리뷰 반영 (R12, R13, R17, D13~D15). 위 설명 중 바뀌는 것:
 *
 * <ul>
 *   <li>로그인은 비교 전에 IP·쌍 순서로 {@code tryAcquire}로 한 번을 예약한다. 쌍이 막히면 IP 예약을 {@code release}한다. 실패는
 *       예약을 그대로 두고, 성공하면 쌍은 {@code reset}, IP는 {@code release}한다 (R13). 그래서 비교 중인 요청이 한도를 이미
 *       차지하고, 동시에 들어온 다음 요청은 비교 전에 401이다 (R12)
 *   <li>{@code TooManySignUpAttemptsException(Duration retryAfter)}, 접근자 {@code Duration retryAfter()}
 *   <li>제한 없는 가입은 {@code createAccount(String email, String password, String nickname, Role role)} (R17)
 * </ul>
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
    private static final UserResponse CREATED = new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG);
    private static final UserResponse RENAMED = new UserResponse(USER_ID, EMAIL, Role.USER, "영희", 2);

    // 제한 횟수는 운영값 (5·50·10)과 달라도 된다. 운영값은 통합 테스트가 확인한다. IP 한도는 쌍 한도보다 커야 둘을 구분할 수 있다
    private static final int PAIR_MAX = 3;
    private static final int IP_MAX = 5;
    private static final int SIGN_UP_MAX = 3;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration SIGN_UP_WINDOW = Duration.ofHours(1);
    // 강도 10의 BCrypt 해시 (D9): $2a$, $2b$, $2y$ 중 하나 + 강도 10 + salt·해시 53자
    private static final String COST_10_BCRYPT = "^\\$2[aby]?\\$10\\$[./A-Za-z0-9]{53}$";

    // 해시 결과를 실제로 확인하려고 진짜 인코더를 쓴다. 강도는 테스트 속도를 위해 최소로 둔다.
    // UserService에 넣는 쪽은 matches 호출 여부 (R3, R7)를 확인하려고 spy로 감싼다
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

    UserService userService;

    @BeforeEach
    void setUp() {
        AuthLimiters limiters = new AuthLimiters(
                new AttemptLimiter(PAIR_MAX, LOGIN_WINDOW, clock),
                new AttemptLimiter(IP_MAX, LOGIN_WINDOW, clock),
                new AttemptLimiter(SIGN_UP_MAX, SIGN_UP_WINDOW, clock));
        userService = new UserService(userRepository, passwordEncoder, assigner, limiters);
    }

    // 가입 (번호 계산·저장은 NicknameTagAssigner의 한 번 시도)

    @Test
    @DisplayName("가입하면 정규화한 이메일과 BCrypt 해시로 한 번 시도를 부르고 그 응답을 돌려준다 (R2, R4)")
    void signUpPassesNormalizedEmailAndHash() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.USER), eq(new Nickname(NICKNAME))))
                .willReturn(CREATED);

        UserResponse response = userService.signUp(RAW_EMAIL, PASSWORD, NICKNAME, clientIp);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(assigner).should().createUser(eq(EMAIL), hash.capture(), eq(Role.USER), eq(new Nickname(NICKNAME)));
        assertThat(hash.getValue()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(hasher.matches(PASSWORD, hash.getValue())).isTrue();
        assertThat(response).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("createAccount는 같은 한 번 시도로 그 role의 회원을 만든다 (dev 테스트 계정, nickname 제약 사항, login-hardening R17)")
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
    @DisplayName("대소문자와 공백만 다른 이메일도 중복이면 저장하지 않고 DuplicateEmailException을 던진다 (R2)")
    void signUpRejectsDuplicateEmail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> userService.signUp(RAW_EMAIL, PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(DuplicateEmailException.class);

        then(assigner).should(never()).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("동시 가입으로 이메일 유니크 제약을 어겨도 500이 아니라 DuplicateEmailException이고 재시도하지 않는다 (R2, nickname 제약 사항)")
    void signUpConvertsEmailUniqueViolation() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(violation(EMAIL_CONSTRAINT));

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp))
                .isInstanceOf(DuplicateEmailException.class);

        then(assigner).should(times(1)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("번호 제약도 이메일 제약도 아닌 제약 위반은 409로 바꾸거나 재시도하지 않고 그대로 던진다 (nickname 제약 사항)")
    void signUpRethrowsOtherConstraintViolation() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        DataIntegrityViolationException other = violation(OTHER_CONSTRAINT);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(other);

        Throwable thrown = catchThrowable(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp));

        assertThat(thrown).isSameAs(other);
        then(assigner).should(times(1)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    // 닉네임 (nickname R1, R7)

    @Test
    @DisplayName("닉네임의 앞뒤 공백을 지운 닉네임으로 한 번 시도를 부른다 (nickname R1)")
    void signUpStripsNickname() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED);

        userService.signUp(EMAIL, PASSWORD, "  " + NICKNAME + " ", clientIp);

        ArgumentCaptor<Nickname> nickname = ArgumentCaptor.forClass(Nickname.class);
        then(assigner).should().createUser(anyString(), anyString(), any(Role.class), nickname.capture());
        assertThat(nickname.getValue().value()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("닉네임은 대소문자를 그대로 둔다 (nickname R7)")
    void signUpKeepsNicknameCase() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED);

        userService.signUp(EMAIL, PASSWORD, "Kim", clientIp);

        then(assigner).should().createUser(anyString(), anyString(), any(Role.class), eq(new Nickname("Kim")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "철수#1", "철\0수", "   "})
    @DisplayName("규칙에 맞지 않는 닉네임은 저장하지 않고 InvalidNicknameException이다 (nickname R1)")
    void signUpRejectsInvalidNickname(String nickname) {
        lenient().when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, nickname, clientIp))
                .isInstanceOf(InvalidNicknameException.class);

        then(assigner).shouldHaveNoInteractions();
    }

    // 번호 경합 재시도 (nickname R4, D4)

    @Test
    @DisplayName("번호 제약을 어기면 한 번 시도를 다시 불러 두 번째 시도의 결과로 가입된다 (nickname R4, D4)")
    void signUpRetriesOnTagConflict() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.USER), eq(new Nickname(NICKNAME))))
                .willThrow(violation(TAG_CONSTRAINT))
                .willReturn(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, 2));

        UserResponse response = userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp);

        assertThat(response).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, 2));
        then(assigner).should(times(2)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("번호 경합이 계속되면 몇 번 재시도한 뒤 멈추고, 이메일 중복이 아닌 예외 (500)로 끝난다 (nickname D4)")
    void signUpGivesUpAfterRepeatedTagConflicts() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(violation(TAG_CONSTRAINT));

        Throwable thrown = catchThrowable(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME, clientIp));

        assertThat(thrown)
                .isNotNull()
                .isNotInstanceOf(DuplicateEmailException.class)
                .isNotInstanceOf(InvalidNicknameException.class);
        // D4 "최대 3번": 첫 시도 포함 3번인지 재시도만 3번인지 문서가 정하지 않아 둘 다 허용한다
        then(assigner).should(atLeast(3)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
        then(assigner).should(atMost(4)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    // 로그인·조회

    @Test
    @DisplayName("정규화한 이메일로 찾아 비밀번호가 맞으면 닉네임과 번호를 포함한 사용자를 돌려준다 (R2, nickname R8)")
    void loginWithNormalizedEmail() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        assertThat(userService.login(RAW_EMAIL, PASSWORD, clientIp))
                .isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 예외와 같은 메시지다 (R5)")
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
    @DisplayName("72바이트를 넘는 비밀번호는 앞 72바이트가 저장된 비밀번호와 같아도 InvalidCredentialsException이다 (D13)")
    void loginRejectsPasswordOverLimit(String storedPassword, String attempt) {
        // 구현이 조회 전에 길이로 거르든 조회 후에 거르든 같은 결과여야 하므로 조회 여부는 강제하지 않는다
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser(storedPassword)));

        assertThatThrownBy(() -> userService.login(EMAIL, attempt, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("정확히 72바이트인 비밀번호는 한도 안이므로 맞으면 로그인된다 (D13, R3)")
    void loginAcceptsPasswordAtLimit() {
        String password = "가".repeat(24);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser(password)));

        assertThat(userService.login(EMAIL, password, clientIp))
                .isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG));
    }

    @Test
    @DisplayName("8자보다 짧은 비밀번호도 저장된 비밀번호와 다르면 InvalidCredentialsException이다 (D13)")
    void loginWithShortPassword() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        assertThatThrownBy(() -> userService.login(EMAIL, "short", clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("이메일 형식이 아닌 값은 없는 이메일과 같이 InvalidCredentialsException이다 (D13)")
    void loginWithNonEmailValue() {
        given(userRepository.findByEmail("not-an-email")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login("not-an-email", PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // 표시 이름에 NUL 문자가 들어가지 않도록 name을 번호로 둔다
    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"user\0@example.com", "\0user@example.com", "user@example.com\0"})
    @DisplayName("NUL 문자가 든 이메일은 Repository를 호출하지 않고 InvalidCredentialsException이다 (D21)")
    void loginWithNulInEmail(String email) {
        assertThatThrownBy(() -> userService.login(email, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("id로 사용자를 찾아 닉네임과 번호를 포함한 응답으로 돌려준다 (nickname R8)")
    void getUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(savedUser()));

        assertThat(userService.getUser(USER_ID)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG));
    }

    @Test
    @DisplayName("관리자를 조회하면 응답의 role이 ADMIN이다 (R3)")
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

    // 닉네임 변경 (nickname R5). 번호 계산과 엔티티 변경은 NicknameTagAssigner의 한 번 시도 (D12)

    @Test
    @DisplayName("공백을 지운 닉네임으로 한 번 시도를 부르고 그 응답을 돌려준다 (nickname R1, R5)")
    void changeNicknameDelegatesToAttempt() {
        given(assigner.changeNickname(USER_ID, new Nickname("영희"))).willReturn(RENAMED);

        UserResponse response = userService.changeNickname(USER_ID, "  영희 ");

        assertThat(response).isEqualTo(RENAMED);
        then(assigner).should(times(1)).changeNickname(anyLong(), any(Nickname.class));
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("닉네임 변경 중 번호 제약을 어기면 한 번 시도를 다시 부른다 (nickname R4, D4)")
    void changeNicknameRetriesOnTagConflict() {
        given(assigner.changeNickname(USER_ID, new Nickname("영희")))
                .willThrow(violation(TAG_CONSTRAINT))
                .willReturn(RENAMED);

        UserResponse response = userService.changeNickname(USER_ID, "영희");

        assertThat(response).isEqualTo(RENAMED);
        then(assigner).should(times(2)).changeNickname(anyLong(), any(Nickname.class));
    }

    @Test
    @DisplayName("닉네임 변경도 번호 경합이 계속되면 몇 번 재시도한 뒤 멈춘다 (nickname D4)")
    void changeNicknameGivesUpAfterRepeatedTagConflicts() {
        given(assigner.changeNickname(anyLong(), any(Nickname.class))).willThrow(violation(TAG_CONSTRAINT));

        Throwable thrown = catchThrowable(() -> userService.changeNickname(USER_ID, "영희"));

        assertThat(thrown).isNotNull().isNotInstanceOf(InvalidNicknameException.class);
        then(assigner).should(atLeast(3)).changeNickname(anyLong(), any(Nickname.class));
        then(assigner).should(atMost(4)).changeNickname(anyLong(), any(Nickname.class));
    }

    @Test
    @DisplayName("닉네임 변경 중 번호 제약이 아닌 제약 위반은 재시도하지 않고 그대로 던진다 (nickname 제약 사항)")
    void changeNicknameRethrowsOtherConstraintViolation() {
        DataIntegrityViolationException other = violation(OTHER_CONSTRAINT);
        given(assigner.changeNickname(USER_ID, new Nickname("영희"))).willThrow(other);

        Throwable thrown = catchThrowable(() -> userService.changeNickname(USER_ID, "영희"));

        assertThat(thrown).isSameAs(other);
        then(assigner).should(times(1)).changeNickname(anyLong(), any(Nickname.class));
    }

    @Test
    @DisplayName("규칙에 맞지 않는 닉네임은 바꾸지 않고 InvalidNicknameException이다 (nickname R1)")
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

    // 로그인 시도 제한 (login-hardening R1~R8, D1, D3, D11)

    @Test
    @DisplayName("없는 이메일도 강도 10의 더미 해시와 BCrypt 비교를 한 번 한다 (R7, D9)")
    void loginWithUnknownEmailComparesDummyHash() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login("nobody@example.com", PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(passwordEncoder).should(times(1)).matches(eq(PASSWORD), hash.capture());
        assertThat(hash.getValue()).matches(COST_10_BCRYPT);
    }

    @Test
    @DisplayName("있는 이메일은 저장된 해시와 한 번만 비교하고 더미 해시와는 비교하지 않는다 (R7)")
    void loginWithKnownEmailComparesStoredHashOnce() {
        User user = savedUser();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(EMAIL, "wrong-password", clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        then(passwordEncoder).should(times(1)).matches(any(), anyString());
        then(passwordEncoder).should().matches("wrong-password", user.getPasswordHash());
    }

    @Test
    @DisplayName("같은 이메일·IP로 한도만큼 틀리면 맞는 비밀번호도 같은 401이고 BCrypt 비교를 하지 않는다 (R1, R3, D3)")
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
    @DisplayName("대소문자와 앞뒤 공백만 다른 이메일의 실패도 같은 쌍으로 센다 (D11)")
    void pairKeyUsesNormalizedEmail() {
        givenSavedUserLeniently();
        failLoginTimes(RAW_EMAIL, clientIp, PAIR_MAX - 1);
        failLoginTimes(EMAIL.toUpperCase(Locale.ROOT), clientIp, 1);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("쌍이 막혀도 다른 IP에서는 맞는 비밀번호로 로그인된다 (D1, D3)")
    void pairBlockDoesNotAffectOtherIp() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);

        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("쌍이 막혀도 같은 IP의 다른 이메일은 로그인된다 (D1)")
    void pairBlockDoesNotAffectOtherEmail() {
        givenSavedUserLeniently();
        String otherEmail = "other@example.com";
        User other = withId(new User(otherEmail, hasher.encode(PASSWORD), new Nickname(NICKNAME), TAG));
        given(userRepository.findByEmail(otherEmail)).willReturn(Optional.of(other));
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);

        assertThat(userService.login(otherEmail, PASSWORD, clientIp).email()).isEqualTo(otherEmail);
    }

    @Test
    @DisplayName("같은 IP에서 여러 이메일로 한도만큼 틀리면 그 IP의 모든 로그인을 BCrypt 없이 거부하고, 다른 IP는 된다 (R2, R3)")
    void ipBlockRejectsAllEmailsFromThatIp() {
        givenSavedUserLeniently();
        for (int i = 0; i < IP_MAX; i++) {
            failLogin("nobody-" + i + "@example.com", clientIp);
        }
        clearInvocations(passwordEncoder);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
        then(passwordEncoder).should(never()).matches(any(), any());

        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("첫 실패부터 15분이 지나면 다시 로그인할 수 있다 (R1, D6)")
    void pairBlockEndsAfterWindow() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);
        clock.advance(LOGIN_WINDOW.minusSeconds(1));
        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);

        clock.advance(Duration.ofSeconds(2));

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("로그인에 성공하면 그 쌍의 실패 횟수는 지우고, IP의 실패 횟수는 남긴다 (R4, R13)")
    void successResetsPairButNotIp() {
        givenSavedUserLeniently();
        // 쌍 2, IP 2
        failLoginTimes(EMAIL, clientIp, PAIR_MAX - 1);
        userService.login(EMAIL, PASSWORD, clientIp);
        // 쌍이 지워지지 않았다면 여기서 쌍 4가 되어 막힌다. IP는 4
        failLoginTimes(EMAIL, clientIp, PAIR_MAX - 1);
        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(CREATED);

        // IP가 지워지지 않았으므로 다른 이메일로 한 번 더 틀리면 IP 5번째 실패로 막힌다
        failLogin("nobody@example.com", clientIp);

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("차단된 동안의 시도는 실패 횟수에 넣지 않는다: IP는 통과했지만 쌍이 막히면 IP 예약을 되돌린다 (R5, D14)")
    void blockedAttemptsAreNotCounted() {
        givenSavedUserLeniently();
        String otherEmail = "other@example.com";
        User other = withId(new User(otherEmail, hasher.encode(PASSWORD), new Nickname(NICKNAME), TAG));
        given(userRepository.findByEmail(otherEmail)).willReturn(Optional.of(other));
        failLoginTimes(EMAIL, clientIp, PAIR_MAX);

        // 쌍이 막힌 상태의 시도. 세었다면 IP가 한도를 넘는다
        failLoginTimes(EMAIL, clientIp, IP_MAX);

        assertThat(userService.login(otherEmail, PASSWORD, clientIp).email()).isEqualTo(otherEmail);
    }

    @Test
    @DisplayName("로그인 성공은 IP 횟수에서도 빠진다: 같은 IP에서 IP 한도보다 많이 성공해도 막히지 않는다 (R13, D14)")
    void successReleasesIpReservation() {
        givenSavedUserLeniently();
        // IP 실패 4번. 성공이 IP 몫을 되돌리지 않으면 다음 성공이 IP 5번째가 되어 그다음부터 막힌다
        for (int i = 0; i < IP_MAX - 1; i++) {
            failLogin("nobody-" + i + "@example.com", clientIp);
        }

        for (int i = 0; i < IP_MAX * 2; i++) {
            assertThat(userService.login(EMAIL, PASSWORD, clientIp))
                    .as("%d번째 성공", i + 1)
                    .isEqualTo(CREATED);
        }

        // 성공은 실패 횟수를 지우지는 않으므로, 한 번 더 틀리면 IP 한도에 닿는다
        failLogin("nobody@example.com", clientIp);
        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("비교 중인 요청이 쌍의 남은 한 번을 차지해서, 동시에 들어온 같은 이메일·IP 요청은 비교 없이 401이다 (R12, D14)")
    void inFlightLoginReservesPairSlot() {
        givenSavedUserLeniently();
        failLoginTimes(EMAIL, clientIp, PAIR_MAX - 1);
        AtomicReference<Throwable> concurrent = whileMatching(() -> userService.login(EMAIL, PASSWORD, clientIp));
        clearInvocations(passwordEncoder);

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(CREATED);

        assertThat(concurrent.get()).isInstanceOf(InvalidCredentialsException.class);
        then(passwordEncoder).should(times(1)).matches(any(), any());
    }

    @Test
    @DisplayName("비교 중인 요청이 IP의 남은 한 번을 차지해서, 동시에 들어온 같은 IP의 다른 이메일 요청은 비교 없이 401이다 (R12, D14)")
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

        assertThat(userService.login(EMAIL, PASSWORD, clientIp)).isEqualTo(CREATED);

        assertThat(concurrent.get()).isInstanceOf(InvalidCredentialsException.class);
        then(passwordEncoder).should(times(1)).matches(any(), any());
    }

    @Test
    @DisplayName("72바이트를 넘는 비밀번호로 거부된 로그인도 실패로 센다 (R6)")
    void passwordOverLimitCountsAsFailure() {
        givenSavedUserLeniently();
        for (int i = 0; i < PAIR_MAX; i++) {
            assertThatThrownBy(() -> userService.login(EMAIL, "a".repeat(73), clientIp))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("NUL 문자가 든 이메일로 거부된 로그인도 실패로 센다 (R6)")
    void nulEmailCountsAsFailure() {
        givenSavedUserLeniently();
        for (int i = 0; i < IP_MAX; i++) {
            failLogin("user" + i + "\0@example.com", clientIp);
        }

        assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD, clientIp))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(userService.login(EMAIL, PASSWORD, otherIp)).isEqualTo(CREATED);
    }

    // 가입 시도 제한 (login-hardening R9, R10, D4, D5)

    @Test
    @DisplayName("같은 IP에서 성공한 가입도 시도로 세어, 한도를 넘는 시도는 아무것도 하지 않고 TooManySignUpAttemptsException이다 (R9)")
    void signUpBlockedAfterLimit() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED);
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
    @DisplayName("이미 가입한 이메일 (409)로 거부된 가입도 시도로 센다 (R9, D4)")
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
    @DisplayName("가입이 막혀도 다른 IP는 가입할 수 있고, 첫 시도부터 1시간이 지나면 풀린다 (R9, D6)")
    void signUpBlockIsPerIpAndEndsAfterWindow() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED);
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
    @DisplayName("가입 차단 예외는 그 IP 창이 끝날 때까지 남은 시간을 담는다 (R15, D15)")
    void signUpBlockCarriesRetryAfter() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED);
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
    @DisplayName("createAccount는 가입 제한을 받지 않는다 (R10, R17)")
    void createAccountIsNotLimited() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willReturn(CREATED);

        for (int i = 0; i < SIGN_UP_MAX * 2; i++) {
            assertThat(userService.createAccount("seed-" + i + "@example.com", PASSWORD, NICKNAME, Role.USER))
                    .isEqualTo(CREATED);
        }

        then(assigner)
                .should(times(SIGN_UP_MAX * 2))
                .createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("TooManySignUpAttemptsException의 메시지는 API 계약의 429 detail 문구다 (D5)")
    void tooManySignUpAttemptsMessage() {
        TooManySignUpAttemptsException exception = new TooManySignUpAttemptsException(Duration.ofMinutes(5));

        assertThat(exception).hasMessage("가입 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.");
        assertThat(exception.retryAfter()).isEqualTo(Duration.ofMinutes(5));
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
        ReflectionTestUtils.setField(user, "id", USER_ID);
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
