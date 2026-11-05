package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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
    @DisplayName("테스트 계정이 없으면 BCrypt 해시로 만든다 (D9, R4)")
    void createsAccountWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);

        new TestAccountInitializer(userRepository, passwordEncoder).run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash()))
                .isTrue();
    }

    @Test
    @DisplayName("테스트 계정이 이미 있으면 만들지 않는다 (D9)")
    void skipsWhenAlreadyExists() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        new TestAccountInitializer(userRepository, passwordEncoder).run(new DefaultApplicationArguments());

        then(userRepository).should(never()).save(any(User.class));
    }
}
