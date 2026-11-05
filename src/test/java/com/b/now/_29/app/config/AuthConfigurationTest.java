package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.b.now._29.app.user.AuthCookies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 실제 application.yaml과 application-dev.yaml을 읽어 JWT_SECRET과 프로필별 설정을 확인한다. */
class AuthConfigurationTest {

    private static final String SECRET_32_BYTES = "12345678901234567890123456789012";
    private static final String SECRET_31_BYTES = "1234567890123456789012345678901";
    private static final String DEV_PROFILE = "spring.profiles.active=dev";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(JwtConfig.class, AuthCookies.class);

    @Test
    @DisplayName("기본 프로필에서 JWT_SECRET이 없으면 시작하지 않는다 (R12)")
    void failsWithoutSecret() {
        assumeThat(System.getenv("JWT_SECRET"))
                .as("환경 변수 JWT_SECRET이 없는 셸에서만 확인할 수 있다")
                .isNull();

        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", SECRET_31_BYTES})
    @DisplayName("기본 프로필에서 JWT_SECRET이 비었거나 32바이트보다 짧으면 시작하지 않는다 (R12)")
    void failsWithShortSecret(String secret) {
        runner.withPropertyValues("JWT_SECRET=" + secret)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("JWT_SECRET이 정확히 32바이트면 시작한다 (R12)")
    void startsWith32ByteSecret() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("길이는 글자 수가 아니라 UTF-8 바이트로 잰다: 한글 11자는 33바이트라 시작한다 (R12)")
    void measuresSecretInBytes() {
        runner.withPropertyValues("JWT_SECRET=가나다라마바사아자차카")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("기본 프로필의 쿠키는 Secure다")
    void defaultProfileCookieIsSecure() {
        runner.withPropertyValues("JWT_SECRET=" + SECRET_32_BYTES).run(context -> {
            AuthCookies cookies = context.getBean(AuthCookies.class);

            assertThat(cookies.issue("token").isSecure()).isTrue();
            assertThat(cookies.expire().isSecure()).isTrue();
        });
    }

    @Test
    @DisplayName("dev 프로필은 JWT_SECRET 없이 시작하고 쿠키의 Secure를 끈다 (D8)")
    void devProfileHasOwnSecretAndInsecureCookie() {
        runner.withPropertyValues(DEV_PROFILE).run(context -> {
            assertThat(context).hasNotFailed();
            AuthCookies cookies = context.getBean(AuthCookies.class);

            assertThat(cookies.issue("token").isSecure()).isFalse();
            assertThat(cookies.expire().isSecure()).isFalse();
        });
    }
}
