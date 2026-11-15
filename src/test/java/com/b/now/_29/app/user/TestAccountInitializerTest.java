package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

/**
 * dev 테스트 계정 (R11, D9, 관리자 R9, nickname 제약 사항, role-owner-category-admin D8).
 *
 * <p>테스트 계정도 번호를 따로 계산하지 않고 {@code UserService.createAccount(String email, String password, String nickname, Role
 * role)}로 만든다 (IP 제한 없음).
 *
 * <p>D8: {@code admin@example.com}은 OWNER가 없을 때만 ({@code UserRepository.existsByRole(Role.OWNER)}가 거짓) OWNER로 만든다.
 * OWNER가 이미 있으면 만들지 않는다.
 * {@code manager@example.com} (CATEGORY_ADMIN, 첫 번째 카테고리 담당)은 dev 프로필 통합 테스트 ({@code
 * TestAccountDevProfileIntegrationTest})에서 확인한다. 이 테스트에서는 manager 계정이 이미 있다고 둔다.
 */
@ExtendWith(MockitoExtension.class)
class TestAccountInitializerTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "test1234!";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "admin1234!";
    private static final String MANAGER_EMAIL = "manager@example.com";
    private static final String NICKNAME = "테스트";
    private static final String ADMIN_NICKNAME = "관리자";

    @Mock
    UserRepository userRepository;

    @Mock
    UserService userService;

    @InjectMocks
    TestAccountInitializer initializer;

    @Test
    @DisplayName("기본 프로필에서는 테스트 계정 생성기가 등록되지 않는다 (R11)")
    void notRegisteredInDefaultProfile() {
        runner().run(context -> assertThat(context).doesNotHaveBean(TestAccountInitializer.class));
    }

    @Test
    @DisplayName("dev가 아닌 프로필에서는 테스트 계정 생성기가 등록되지 않는다 (R11)")
    void notRegisteredInOtherProfile() {
        runner().withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(TestAccountInitializer.class));
    }

    @Test
    @DisplayName("테스트 계정 생성기는 dev 프로필 전용이다 (R11). 실제 등록은 TestAccountDevProfileIntegrationTest가 확인한다")
    void isDevProfileOnly() {
        assertThat(TestAccountInitializer.class.getAnnotation(Profile.class).value())
                .containsExactly("dev");
    }

    @Test
    @DisplayName("테스트 계정이 없으면 UserService 가입 경로로 USER 계정을 만든다 (D9, R4, nickname 제약 사항)")
    void createsAccountWhenMissing() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(MANAGER_EMAIL)).willReturn(true);

        initializer.run(new DefaultApplicationArguments());

        then(userService).should().createAccount(EMAIL, PASSWORD, NICKNAME, Role.USER);
        then(userService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("관리자 테스트 계정이 없고 OWNER도 없으면 OWNER 계정으로 만든다 (role-owner-category-admin D8)")
    void createsOwnerAccountWhenNoOwner() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(MANAGER_EMAIL)).willReturn(true);
        given(userRepository.existsByRole(Role.OWNER)).willReturn(false);

        initializer.run(new DefaultApplicationArguments());

        then(userService).should().createAccount(ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME, Role.OWNER);
        then(userService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("OWNER가 이미 있으면 관리자 테스트 계정을 어떤 역할로도 만들지 않는다 (OWNER는 1명, R2, D8)")
    void doesNotCreateAdminAccountWhenOwnerExists() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(MANAGER_EMAIL)).willReturn(true);
        given(userRepository.existsByRole(Role.OWNER)).willReturn(true);

        initializer.run(new DefaultApplicationArguments());

        then(userService).should(never()).createAccount(eq(ADMIN_EMAIL), anyString(), anyString(), any(Role.class));
        then(userService).should(never()).createAccount(anyString(), anyString(), anyString(), eq(Role.OWNER));
    }

    @Test
    @DisplayName("번호를 직접 계산하거나 저장하지 않는다. 번호 규칙은 UserService만 안다 (nickname 제약 사항)")
    void doesNotComputeTagItself() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(false);
        given(userRepository.existsByEmail(MANAGER_EMAIL)).willReturn(true);
        given(userRepository.existsByRole(Role.OWNER)).willReturn(false);

        initializer.run(new DefaultApplicationArguments());

        then(userRepository).should(never()).findMaxTagByNickname(anyString());
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("테스트 계정이 모두 이미 있으면 만들지 않는다 (D9)")
    void skipsWhenAlreadyExists() throws Exception {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(ADMIN_EMAIL)).willReturn(true);
        given(userRepository.existsByEmail(MANAGER_EMAIL)).willReturn(true);

        initializer.run(new DefaultApplicationArguments());

        then(userService).shouldHaveNoInteractions();
        then(userRepository).should(never()).save(any(User.class));
    }

    /** 프로필 조건만 보므로 의존 빈은 모두 mock이다. dev가 아니면 생성기를 만들지 않아 의존이 더 있어도 상관없다. */
    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(UserService.class, () -> mock(UserService.class))
                .withUserConfiguration(TestAccountInitializer.class);
    }
}
