package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TestAccountInitializerTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "test1234!";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "admin1234!";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PasswordEncoder.class, () -> passwordEncoder)
            .withUserConfiguration(TestAccountInitializer.class);

    @Mock
    UserRepository userRepository;

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
    @DisplayName("테스트 계정이 없으면 BCrypt 해시와 USER role로 만든다 (D9, R4, 관리자 R9)")
    void createsAccountWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(true);

        new TestAccountInitializer(userRepository, passwordEncoder).run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
    }

    @Test
    @DisplayName("관리자 테스트 계정이 없으면 BCrypt 해시와 ADMIN role로 만든다 (R9, D9)")
    void createsAdminAccountWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);

        new TestAccountInitializer(userRepository, passwordEncoder).run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
    }

    @Test
    @DisplayName("두 계정이 모두 없으면 둘 다 만든다 (R9)")
    void createsBothAccountsWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);

        new TestAccountInitializer(userRepository, passwordEncoder).run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should(times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(User::getEmail, User::getRole)
                .containsExactlyInAnyOrder(tuple(EMAIL, Role.USER), tuple(ADMIN_EMAIL, Role.ADMIN));
    }

    @Test
    @DisplayName("두 테스트 계정이 이미 있으면 만들지 않는다 (D9)")
    void skipsWhenAlreadyExists() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(true);

        new TestAccountInitializer(userRepository, passwordEncoder).run(new DefaultApplicationArguments());

        then(userRepository).should(never()).save(any(User.class));
    }
}
