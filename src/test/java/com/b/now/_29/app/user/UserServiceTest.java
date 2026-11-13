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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;
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

    // 해시 결과를 실제로 확인하려고 진짜 인코더를 쓴다. 강도는 테스트 속도를 위해 최소로 둔다
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    UserRepository userRepository;

    @Mock
    NicknameTagAssigner assigner;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, assigner);
    }

    // 가입 (번호 계산·저장은 NicknameTagAssigner의 한 번 시도)

    @Test
    @DisplayName("가입하면 정규화한 이메일과 BCrypt 해시로 한 번 시도를 부르고 그 응답을 돌려준다 (R2, R4)")
    void signUpPassesNormalizedEmailAndHash() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.USER), eq(new Nickname(NICKNAME))))
                .willReturn(CREATED);

        UserResponse response = userService.signUp(RAW_EMAIL, PASSWORD, NICKNAME);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(assigner).should().createUser(eq(EMAIL), hash.capture(), eq(Role.USER), eq(new Nickname(NICKNAME)));
        assertThat(hash.getValue()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash.getValue())).isTrue();
        assertThat(response).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("role을 받는 가입 경로는 같은 한 번 시도로 그 role의 회원을 만든다 (dev 테스트 계정, nickname 제약 사항)")
    void signUpWithRole() {
        UserResponse admin = new UserResponse(USER_ID, EMAIL, Role.ADMIN, NICKNAME, 5);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(eq(EMAIL), anyString(), eq(Role.ADMIN), eq(new Nickname(NICKNAME))))
                .willReturn(admin);

        UserResponse response = userService.signUp(EMAIL, PASSWORD, NICKNAME, Role.ADMIN);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        then(assigner).should().createUser(eq(EMAIL), hash.capture(), eq(Role.ADMIN), eq(new Nickname(NICKNAME)));
        assertThat(passwordEncoder.matches(PASSWORD, hash.getValue())).isTrue();
        assertThat(response).isEqualTo(admin);
    }

    @Test
    @DisplayName("대소문자와 공백만 다른 이메일도 중복이면 저장하지 않고 DuplicateEmailException을 던진다 (R2)")
    void signUpRejectsDuplicateEmail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> userService.signUp(RAW_EMAIL, PASSWORD, NICKNAME))
                .isInstanceOf(DuplicateEmailException.class);

        then(assigner).should(never()).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("동시 가입으로 이메일 유니크 제약을 어겨도 500이 아니라 DuplicateEmailException이고 재시도하지 않는다 (R2, nickname 제약 사항)")
    void signUpConvertsEmailUniqueViolation() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(violation(EMAIL_CONSTRAINT));

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME))
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

        Throwable thrown = catchThrowable(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME));

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

        userService.signUp(EMAIL, PASSWORD, "  " + NICKNAME + " ");

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

        userService.signUp(EMAIL, PASSWORD, "Kim");

        then(assigner).should().createUser(anyString(), anyString(), any(Role.class), eq(new Nickname("Kim")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "철수#1", "철\0수", "   "})
    @DisplayName("규칙에 맞지 않는 닉네임은 저장하지 않고 InvalidNicknameException이다 (nickname R1)")
    void signUpRejectsInvalidNickname(String nickname) {
        lenient().when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD, nickname))
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

        UserResponse response = userService.signUp(EMAIL, PASSWORD, NICKNAME);

        assertThat(response).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, 2));
        then(assigner).should(times(2)).createUser(anyString(), anyString(), any(Role.class), any(Nickname.class));
    }

    @Test
    @DisplayName("번호 경합이 계속되면 몇 번 재시도한 뒤 멈추고, 이메일 중복이 아닌 예외 (500)로 끝난다 (nickname D4)")
    void signUpGivesUpAfterRepeatedTagConflicts() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(assigner.createUser(anyString(), anyString(), any(Role.class), any(Nickname.class)))
                .willThrow(violation(TAG_CONSTRAINT));

        Throwable thrown = catchThrowable(() -> userService.signUp(EMAIL, PASSWORD, NICKNAME));

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

        assertThat(userService.login(RAW_EMAIL, PASSWORD))
                .isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 예외와 같은 메시지다 (R5)")
    void loginFailuresAreIndistinguishable() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        Throwable unknownEmail = catchThrowable(() -> userService.login("nobody@example.com", PASSWORD));
        Throwable wrongPassword = catchThrowable(() -> userService.login(EMAIL, "wrong-password"));

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

        assertThatThrownBy(() -> userService.login(EMAIL, attempt)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("정확히 72바이트인 비밀번호는 한도 안이므로 맞으면 로그인된다 (D13, R3)")
    void loginAcceptsPasswordAtLimit() {
        String password = "가".repeat(24);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser(password)));

        assertThat(userService.login(EMAIL, password))
                .isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, NICKNAME, TAG));
    }

    @Test
    @DisplayName("8자보다 짧은 비밀번호도 저장된 비밀번호와 다르면 InvalidCredentialsException이다 (D13)")
    void loginWithShortPassword() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        assertThatThrownBy(() -> userService.login(EMAIL, "short")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("이메일 형식이 아닌 값은 없는 이메일과 같이 InvalidCredentialsException이다 (D13)")
    void loginWithNonEmailValue() {
        given(userRepository.findByEmail("not-an-email")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login("not-an-email", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // 표시 이름에 NUL 문자가 들어가지 않도록 name을 번호로 둔다
    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"user\0@example.com", "\0user@example.com", "user@example.com\0"})
    @DisplayName("NUL 문자가 든 이메일은 Repository를 호출하지 않고 InvalidCredentialsException이다 (D21)")
    void loginWithNulInEmail(String email) {
        assertThatThrownBy(() -> userService.login(email, PASSWORD)).isInstanceOf(InvalidCredentialsException.class);

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
                .willReturn(Optional.of(withId(
                        new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN, new Nickname(NICKNAME), TAG))));

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

    private User savedUser() {
        return savedUser(PASSWORD);
    }

    private User savedUser(String password) {
        return withId(new User(EMAIL, passwordEncoder.encode(password), new Nickname(NICKNAME), TAG));
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
