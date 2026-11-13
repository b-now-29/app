package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.util.Optional;
import java.util.stream.Stream;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String RAW_EMAIL = "  User@Example.COM ";
    private static final String PASSWORD = "password1234";

    // 해시 결과를 실제로 확인하려고 진짜 인코더를 쓴다. 강도는 테스트 속도를 위해 최소로 둔다
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    UserRepository userRepository;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("가입하면 정규화한 이메일과 BCrypt 해시를 저장하고 해시 없는 응답을 돌려준다 (R2, R4)")
    void signUpStoresNormalizedEmailAndHash() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> withId(invocation.getArgument(0)));

        UserResponse response = userService.signUp(RAW_EMAIL, PASSWORD);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
        assertThat(response).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER));
    }

    @Test
    @DisplayName("대소문자와 공백만 다른 이메일도 중복이면 저장하지 않고 DuplicateEmailException을 던진다 (R2)")
    void signUpRejectsDuplicateEmail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> userService.signUp(RAW_EMAIL, PASSWORD)).isInstanceOf(DuplicateEmailException.class);

        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("동시 가입으로 유니크 제약을 어겨도 500이 아니라 DuplicateEmailException이다 (R2)")
    void signUpConvertsUniqueViolation() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> userService.signUp(EMAIL, PASSWORD)).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("정규화한 이메일로 찾아 비밀번호가 맞으면 사용자를 돌려준다 (R2)")
    void loginWithNormalizedEmail() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(savedUser()));

        assertThat(userService.login(RAW_EMAIL, PASSWORD)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER));
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

        assertThat(userService.login(EMAIL, password)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER));
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
    @DisplayName("id로 사용자를 찾아 응답으로 돌려준다")
    void getUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(savedUser()));

        assertThat(userService.getUser(USER_ID)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER));
    }

    @Test
    @DisplayName("관리자를 조회하면 응답의 role이 ADMIN이다 (R3)")
    void getAdminUser() {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(withId(new User(EMAIL, passwordEncoder.encode(PASSWORD), Role.ADMIN))));

        assertThat(userService.getUser(USER_ID)).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.ADMIN));
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 없으면 UserNotFoundException을 던진다")
    void getUnknownUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(USER_ID)).isInstanceOf(UserNotFoundException.class);
    }

    private User savedUser() {
        return savedUser(PASSWORD);
    }

    private User savedUser(String password) {
        return withId(new User(EMAIL, passwordEncoder.encode(password)));
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
