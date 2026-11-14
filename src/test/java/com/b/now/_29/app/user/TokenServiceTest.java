package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.b.now._29.app.config.JwtConfig;
import com.b.now._29.app.support.TestJwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 토큰 기한 1시간과 자동 연장 (docs/features/token-sliding-expiry.md R1~R6, R10, R12, R13, D3, D5, D8, D17).
 *
 * <p>{@code TokenService}와 디코더는 운영과 같은 {@code JwtConfig}로 Spring 컨텍스트에서 만들고, {@code Clock} 빈만 고정 시계로 둔다. 고정 시각을
 * 실제 지금과 먼 2030년으로 두어, 어느 쪽이든 시스템 시계를 쓰면 테스트가 실패하게 한다 (R10, D8, D17).
 */
@SpringJUnitConfig({JwtConfig.class, TokenService.class, TokenServiceTest.FixedClockConfig.class})
@TestPropertySource(properties = "app.jwt.secret=" + TestJwts.SECRET)
class TokenServiceTest {

    private static final long USER_ID = 1L;

    private static final Instant NOW = Instant.parse("2030-03-15T09:30:00Z");

    @Configuration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    JwtDecoder decoder;

    @Autowired
    TokenService tokenService;

    // 발급 (R1)

    @Test
    @DisplayName("HS256으로 서명하고 sub에 사용자 id, exp는 1시간 뒤, auth_time은 발급 시각이다 (R1)")
    void issuesOneHourTokenWithAuthTime() {
        IssuedToken issued = tokenService.issue(USER_ID);

        Jwt jwt = decoder.decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(authTimeOf(jwt)).isEqualTo(NOW.getEpochSecond());
        assertThat(issued.maxAge()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("직접 발급한 토큰에서 사용자 id를 읽는다")
    void parsesIssuedToken() {
        assertThat(tokenService.parseUserId(tokenService.issue(USER_ID).value()))
                .contains(USER_ID);
    }

    @Test
    @DisplayName("같은 키로 서명한 다른 곳의 유효한 토큰도 읽는다")
    void parsesValidToken() {
        assertThat(tokenService.parseUserId(TestJwts.valid(USER_ID, NOW))).contains(USER_ID);
    }

    @Test
    @DisplayName("auth_time이 1일 가까이 지났어도 1일 이내면 읽는다 (R6)")
    void parsesTokenWithinAbsoluteLimit() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(23)), NOW.plus(Duration.ofMinutes(20)));

        assertThat(tokenService.parseUserId(token)).contains(USER_ID);
    }

    @Test
    @DisplayName("exp가 1초 남은 토큰은 읽는다 (R12의 바로 앞 경계)")
    void parsesTokenExpiringInOneSecond() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).plusSeconds(1), NOW.plusSeconds(1));

        assertThat(tokenService.parseUserId(token)).contains(USER_ID);
    }

    @Test
    @DisplayName("헤더 typ이 JWT인 토큰은 읽는다 (R13: JWT 타입은 통과)")
    void parsesTokenWithJwtType() {
        assertThat(tokenService.parseUserId(TestJwts.withType(USER_ID, "JWT", NOW)))
                .contains(USER_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 토큰은 예외 없이 빈 값이다 (D3, R5, R6, R12, R13)")
    void invalidTokenIsEmpty(String description, String token) {
        assertThat(tokenService.parseUserId(token)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 토큰은 연장하지 않는다 (R7, R12, R13)")
    void invalidTokenIsNotRenewed(String description, String token) {
        assertThat(tokenService.renew(token)).isEmpty();
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                arguments("만료", TestJwts.expired(USER_ID, NOW)),
                arguments(
                        "exp가 1초 지남 (R10: 허용 오차 0)",
                        TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).minusSeconds(1), NOW.minusSeconds(1))),
                arguments("exp가 정확히 지금 (R12)", TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)), NOW)),
                arguments("헤더 typ이 at+jwt (R13)", TestJwts.withType(USER_ID, "at+jwt", NOW)),
                arguments("다른 키로 서명", TestJwts.signedWithOtherKey(USER_ID, NOW)),
                arguments("본문 변조", TestJwts.tampered(USER_ID, 2L, NOW)),
                arguments("서명 없음 (alg: none)", TestJwts.unsigned(USER_ID, NOW)),
                arguments("숫자가 아닌 sub", TestJwts.withNonNumericSubject(NOW)),
                arguments("auth_time 없음 (배포 전 7일 토큰)", TestJwts.withoutAuthTime(USER_ID, NOW)),
                arguments(
                        "auth_time이 1일보다 오래됨",
                        TestJwts.of(
                                USER_ID,
                                NOW.minus(Duration.ofDays(1)).minusSeconds(1),
                                NOW.plus(Duration.ofMinutes(20)))),
                arguments("JWT 형식이 아님", "not-a-jwt"),
                arguments("빈 문자열", ""));
    }

    // 연장 (R2~R4)

    @Test
    @DisplayName("남은 시간이 1시간이면 연장하지 않는다 (R2, D2)")
    void freshTokenIsNotRenewed() {
        String token = TestJwts.of(USER_ID, NOW, NOW.plus(Duration.ofHours(1)));

        assertThat(tokenService.renew(token)).isEmpty();
    }

    @Test
    @DisplayName("남은 시간이 정확히 30분이면 연장하지 않는다 (R2: 30분 미만만)")
    void tokenWithThirtyMinutesLeftIsNotRenewed() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(30)));

        assertThat(tokenService.renew(token)).isEmpty();
    }

    @Test
    @DisplayName("남은 시간이 30분 미만이면 같은 sub·auth_time으로 exp가 지금 + 1시간인 새 토큰을 준다 (R2, R3)")
    void tokenWithLessThanThirtyMinutesLeftIsRenewed() {
        Instant authTime = NOW.minus(Duration.ofMinutes(30)).minusSeconds(1);
        String token =
                TestJwts.of(USER_ID, authTime, NOW.plus(Duration.ofMinutes(30)).minusSeconds(1));

        IssuedToken renewed = tokenService.renew(token).orElseThrow();

        Jwt jwt = decoder.decode(renewed.value());
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(authTimeOf(jwt)).isEqualTo(authTime.getEpochSecond());
        assertThat(renewed.maxAge()).isEqualTo(Duration.ofHours(1));
        assertThat(tokenService.parseUserId(renewed.value())).contains(USER_ID);
    }

    @Test
    @DisplayName("exp가 1초 남은 토큰은 연장한다 (R12의 바로 앞 경계)")
    void tokenExpiringInOneSecondIsRenewed() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).plusSeconds(1), NOW.plusSeconds(1));

        assertThat(tokenService.renew(token)).isPresent();
    }

    @Test
    @DisplayName("연장한 토큰의 exp는 auth_time + 1일을 넘지 않고, maxAge는 그때까지 남은 시간이다 (R3, D3)")
    void renewedExpiryIsCappedByAbsoluteLimit() {
        Instant authTime = NOW.minus(Duration.ofHours(23)).minus(Duration.ofMinutes(30));
        String token = TestJwts.of(USER_ID, authTime, NOW.plus(Duration.ofMinutes(10)));

        IssuedToken renewed = tokenService.renew(token).orElseThrow();

        Jwt jwt = decoder.decode(renewed.value());
        assertThat(jwt.getExpiresAt()).isEqualTo(authTime.plus(Duration.ofDays(1)));
        assertThat(authTimeOf(jwt)).isEqualTo(authTime.getEpochSecond());
        assertThat(renewed.maxAge()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("이미 절대 기한까지 연장된 토큰은 새 exp가 늦지 않으므로 연장하지 않는다 (R4)")
    void tokenAtAbsoluteLimitIsNotRenewed() {
        Instant authTime = NOW.minus(Duration.ofHours(23)).minus(Duration.ofMinutes(50));
        String token = TestJwts.of(USER_ID, authTime, authTime.plus(Duration.ofDays(1)));

        assertThat(tokenService.renew(token)).isEmpty();
        assertThat(tokenService.parseUserId(token)).contains(USER_ID);
    }

    @Test
    @DisplayName("방금 발급한 토큰은 연장하지 않는다 (R2)")
    void justIssuedTokenIsNotRenewed() {
        assertThat(tokenService.renew(tokenService.issue(USER_ID).value())).isEmpty();
    }

    private static long authTimeOf(Jwt jwt) {
        Object value = jwt.getClaims().get(TestJwts.AUTH_TIME);
        assertThat(value).as("auth_time은 epoch 초 숫자다 (D3)").isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }
}
