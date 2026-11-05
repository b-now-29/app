package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.b.now._29.app.config.JwtConfig;
import com.b.now._29.app.support.TestJwts;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class TokenServiceTest {

    private static final long USER_ID = 1L;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("JWT_SECRET=" + TestJwts.SECRET)
            .withUserConfiguration(JwtConfig.class, TokenService.class);

    @Test
    @DisplayName("HS256으로 서명하고 sub에 사용자 id, 수명은 7일이다 (D2, Q3)")
    void issuesHs256TokenWithUserIdAndSevenDayLifetime() {
        runner.run(context -> {
            String token = context.getBean(TokenService.class).issue(USER_ID);

            Jwt jwt = NimbusJwtDecoder.withSecretKey(TestJwts.key())
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build()
                    .decode(token);
            assertThat(jwt.getSubject()).isEqualTo("1");
            assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    @DisplayName("직접 발급한 토큰에서 사용자 id를 읽는다")
    void parsesIssuedToken() {
        runner.run(context -> {
            TokenService tokenService = context.getBean(TokenService.class);

            assertThat(tokenService.parseUserId(tokenService.issue(USER_ID))).contains(USER_ID);
        });
    }

    @Test
    @DisplayName("같은 키로 서명한 다른 곳의 유효한 토큰도 읽는다")
    void parsesValidToken() {
        runner.run(context -> assertThat(context.getBean(TokenService.class).parseUserId(TestJwts.valid(USER_ID)))
                .contains(USER_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 토큰은 예외 없이 빈 값이다 (D3)")
    void invalidTokenIsEmpty(String description, String token) {
        runner.run(context -> assertThat(context.getBean(TokenService.class).parseUserId(token))
                .isEmpty());
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                arguments("만료", TestJwts.expired(USER_ID)),
                arguments("다른 키로 서명", TestJwts.signedWithOtherKey(USER_ID)),
                arguments("본문 변조", TestJwts.tampered(USER_ID, 2L)),
                arguments("서명 없음 (alg: none)", TestJwts.unsigned(USER_ID)),
                arguments("숫자가 아닌 sub", TestJwts.withNonNumericSubject()),
                arguments("JWT 형식이 아님", "not-a-jwt"),
                arguments("빈 문자열", ""));
    }
}
