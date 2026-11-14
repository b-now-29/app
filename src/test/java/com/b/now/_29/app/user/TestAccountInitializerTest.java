package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * dev 테스트 계정 (R11, D9, 관리자 R9, nickname 제약 사항).
 *
 * <p>(코드 리뷰) 테스트 계정도 번호를 따로 계산하지 않고 {@code UserService}의 가입 경로 (비밀번호 해시, 번호 계산, 번호 경합 재시도)를 쓴다.
 * 구현할 것:
 *
 * <ul>
 *   <li>{@code TestAccountInitializer(UserRepository userRepository, UserService userService)} — 계정이 있는지는
 *       {@code existsByEmail}로 보고, 없으면 {@code userService.signUp}으로 만든다. {@code PasswordEncoder}는 더 받지 않는다
 *   <li>{@code UserService.signUp(String email, String password, String nickname, Role role): UserResponse} — 기존
 *       {@code signUp(email, password, nickname)}은 {@code Role.USER}로 이것을 부른다
 * </ul>
 *
 * <p>(login-hardening R17) 제한 없는 가입 경로는 {@code UserService.createAccount(String email, String password, String
 * nickname, Role role)}로 이름이 바뀐다. 테스트 계정은 IP 제한을 받지 않으므로 이것을 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class TestAccountInitializerTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "test1234!";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "admin1234!";
    private static final String NICKNAME = "테스트";
    private static final String ADMIN_NICKNAME = "관리자";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(UserService.class, () -> mock(UserService.class))
            .withUserConfiguration(TestAccountInitializer.class);

    @Mock
    UserRepository userRepository;

    @Mock
    UserService userService;

    @Test
    @DisplayName("기본 프로필에서는 테스트 계정 생성기가 등록되지 않는다 (R11)")
    void notRegisteredInDefaultProfile() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TestAccountInitializer.class));
    }

    @Test
    @DisplayName("dev가 아닌 프로필에서는 테스트 계정 생성기가 등록되지 않는다 (R11)")
    void notRegisteredInOtherProfile() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(TestAccountInitializer.class));
    }

    @Test
    @DisplayName("dev 프로필에서는 테스트 계정 생성기가 등록된다 (R11)")
    void registeredInDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(TestAccountInitializer.class));
    }

    @Test
    @DisplayName("테스트 계정이 없으면 UserService 가입 경로로 USER 계정을 만든다 (D9, R4, nickname 제약 사항)")
    void createsAccountWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(true);

        initializer().run(new DefaultApplicationArguments());

        then(userService).should().createAccount(EMAIL, PASSWORD, NICKNAME, Role.USER);
        then(userService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("관리자 테스트 계정이 없으면 UserService 가입 경로로 ADMIN 계정을 만든다 (R9, D9, nickname 제약 사항)")
    void createsAdminAccountWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);

        initializer().run(new DefaultApplicationArguments());

        then(userService).should().createAccount(ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME, Role.ADMIN);
        then(userService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("두 계정이 모두 없으면 둘 다 만든다 (R9)")
    void createsBothAccountsWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);

        initializer().run(new DefaultApplicationArguments());

        then(userService).should().createAccount(EMAIL, PASSWORD, NICKNAME, Role.USER);
        then(userService).should().createAccount(ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME, Role.ADMIN);
    }

    @Test
    @DisplayName("번호를 직접 계산하거나 저장하지 않는다. 번호 규칙은 UserService만 안다 (nickname 제약 사항)")
    void doesNotComputeTagItself() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);

        initializer().run(new DefaultApplicationArguments());

        then(userRepository).should(never()).findMaxTagByNickname(anyString());
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("두 테스트 계정이 이미 있으면 만들지 않는다 (D9)")
    void skipsWhenAlreadyExists() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(true);

        initializer().run(new DefaultApplicationArguments());

        then(userService).shouldHaveNoInteractions();
        then(userRepository).should(never()).save(any(User.class));
    }

    private TestAccountInitializer initializer() {
        return new TestAccountInitializer(userRepository, userService);
    }
}
