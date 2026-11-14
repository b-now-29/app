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
 * 토큰의 발급·해석·연장을 확인한다. 토큰은 1시간짜리이고 남은 시간이 짧으면 로그인 시각부터 1일까지 연장되며, 로그아웃으로 무효화할 수 있도록 회원의 토큰 버전을
 * {@code ver} 클레임에 담는다.
 *
 * <p>{@code TokenService}와 디코더는 운영과 같은 {@code JwtConfig}로 Spring 컨텍스트에서 만들고, {@code Clock} 빈만 고정 시계로 둔다. 고정 시각을
 * 실제 지금과 먼 2030년으로 두어, 어느 쪽이든 시스템 시계를 쓰면 테스트가 실패하게 한다. 만료 검사와 연장 계산이 같은 시계를 써야 경계에서 판단이 엇갈리지 않기
 * 때문이다.
 *
 * <p>발급하는 토큰의 버전은 새 회원의 기본값 0과 구별되도록 0이 아닌 값을 쓴다.
 */
@SpringJUnitConfig({JwtConfig.class, TokenService.class, TokenServiceTest.FixedClockConfig.class})
@TestPropertySource(properties = "app.jwt.secret=" + TestJwts.SECRET)
class TokenServiceTest {

    private static final long USER_ID = 1L;

    private static final int VERSION = 4;

    private static final TokenSubject SUBJECT = new TokenSubject(USER_ID, VERSION);

    /** {@link TestJwts}가 버전을 따로 받지 않을 때 넣는 값의 토큰 주체. */
    private static final TokenSubject DEFAULT_SUBJECT = new TokenSubject(USER_ID, TestJwts.DEFAULT_VERSION);

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

    // 발급

    @Test
    @DisplayName("HS256으로 서명하고 sub에 사용자 id, exp는 1시간 뒤, auth_time은 발급 시각이다")
    void issuesOneHourTokenWithAuthTime() {
        IssuedToken issued = tokenService.issue(SUBJECT);

        Jwt jwt = decoder.decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(authTimeOf(jwt)).isEqualTo(NOW.getEpochSecond());
        assertThat(issued.maxAge()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("발급한 토큰의 ver 클레임은 받은 토큰 버전이다")
    void issuedTokenCarriesVersion() {
        Jwt jwt = decoder.decode(tokenService.issue(SUBJECT).value());

        assertThat(versionOf(jwt)).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("직접 발급한 토큰에서 사용자 id와 토큰 버전을 읽는다")
    void parsesIssuedToken() {
        assertThat(tokenService.parse(tokenService.issue(SUBJECT).value())).contains(SUBJECT);
    }

    @Test
    @DisplayName("같은 키로 서명한 다른 곳의 유효한 토큰도 읽는다")
    void parsesValidToken() {
        assertThat(tokenService.parse(TestJwts.valid(USER_ID, NOW))).contains(DEFAULT_SUBJECT);
    }

    @Test
    @DisplayName("ver 클레임의 값을 그대로 토큰 버전으로 읽는다")
    void parsesVersionClaim() {
        assertThat(tokenService.parse(TestJwts.withVersion(USER_ID, 7, NOW))).contains(new TokenSubject(USER_ID, 7));
    }

    @Test
    @DisplayName("auth_time이 1일 가까이 지났어도 1일 이내면 읽는다")
    void parsesTokenWithinAbsoluteLimit() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(23)), NOW.plus(Duration.ofMinutes(20)));

        assertThat(tokenService.parse(token)).contains(DEFAULT_SUBJECT);
    }

    @Test
    @DisplayName("exp가 1초 남은 토큰은 읽는다 (exp가 지금과 같으면 거부하는 경계의 바로 앞)")
    void parsesTokenExpiringInOneSecond() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).plusSeconds(1), NOW.plusSeconds(1));

        assertThat(tokenService.parse(token)).contains(DEFAULT_SUBJECT);
    }

    @Test
    @DisplayName("헤더 typ 검사는 다른 종류의 JWT만 거르므로, typ이 JWT인 토큰은 읽는다")
    void parsesTokenWithJwtType() {
        assertThat(tokenService.parse(TestJwts.withType(USER_ID, "JWT", NOW))).contains(DEFAULT_SUBJECT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 토큰은 요청을 거부하지 않고 비회원으로 처리하도록 예외 없이 빈 값이다")
    void invalidTokenIsEmpty(String description, String token) {
        assertThat(tokenService.parse(token)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("잘못된 토큰은 연장하지 않는다")
    void invalidTokenIsNotRenewed(String description, String token) {
        assertThat(tokenService.renew(token)).isEmpty();
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                arguments("만료", TestJwts.expired(USER_ID, NOW)),
                arguments(
                        "exp가 1초 지남 (허용 오차 0)",
                        TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).minusSeconds(1), NOW.minusSeconds(1))),
                arguments("exp가 정확히 지금", TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)), NOW)),
                arguments("헤더 typ이 at+jwt", TestJwts.withType(USER_ID, "at+jwt", NOW)),
                arguments("다른 키로 서명", TestJwts.signedWithOtherKey(USER_ID, NOW)),
                arguments("본문 변조", TestJwts.tampered(USER_ID, 2L, NOW)),
                arguments("서명 없음 (alg: none)", TestJwts.unsigned(USER_ID, NOW)),
                arguments("숫자가 아닌 sub", TestJwts.withNonNumericSubject(NOW)),
                arguments("auth_time 없음 (배포 전 7일 토큰)", TestJwts.withoutAuthTime(USER_ID, NOW)),
                arguments("ver 없음 (토큰 무효화 배포 전 토큰)", TestJwts.withoutVersion(USER_ID, NOW)),
                arguments(
                        "ver 없고 남은 시간이 30분 미만",
                        TestJwts.withoutVersion(
                                USER_ID, NOW.minus(Duration.ofMinutes(50)), NOW.plus(Duration.ofMinutes(10)))),
                arguments(
                        "auth_time이 1일보다 오래됨",
                        TestJwts.of(
                                USER_ID,
                                NOW.minus(Duration.ofDays(1)).minusSeconds(1),
                                NOW.plus(Duration.ofMinutes(20)))),
                arguments("JWT 형식이 아님", "not-a-jwt"),
                arguments("빈 문자열", ""));
    }

    // 연장: 요청마다 서명과 Set-Cookie를 하지 않도록 남은 시간이 30분 미만일 때만 다시 발급한다

    @Test
    @DisplayName("남은 시간이 1시간이면 연장하지 않는다")
    void freshTokenIsNotRenewed() {
        String token = TestJwts.of(USER_ID, NOW, NOW.plus(Duration.ofHours(1)));

        assertThat(tokenService.renew(token)).isEmpty();
    }

    @Test
    @DisplayName("남은 시간이 정확히 30분이면 30분 미만이 아니므로 연장하지 않는다")
    void tokenWithThirtyMinutesLeftIsNotRenewed() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofMinutes(30)), NOW.plus(Duration.ofMinutes(30)));

        assertThat(tokenService.renew(token)).isEmpty();
    }

    @Test
    @DisplayName("남은 시간이 30분 미만이면 같은 sub·auth_time으로 exp가 지금 + 1시간인 새 토큰을 준다")
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
        assertThat(tokenService.parse(renewed.value())).contains(DEFAULT_SUBJECT);
    }

    @Test
    @DisplayName("연장한 토큰의 ver는 원래 토큰의 값 그대로다")
    void renewedTokenKeepsVersion() {
        String token =
                TestJwts.of(USER_ID, VERSION, NOW.minus(Duration.ofMinutes(50)), NOW.plus(Duration.ofMinutes(10)));

        IssuedToken renewed = tokenService.renew(token).orElseThrow();

        assertThat(versionOf(decoder.decode(renewed.value()))).isEqualTo(VERSION);
        assertThat(tokenService.parse(renewed.value())).contains(SUBJECT);
    }

    @Test
    @DisplayName("exp가 1초 남은 토큰은 연장한다 (exp가 지금과 같으면 거부하는 경계의 바로 앞)")
    void tokenExpiringInOneSecondIsRenewed() {
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).plusSeconds(1), NOW.plusSeconds(1));

        assertThat(tokenService.renew(token)).isPresent();
    }

    @Test
    @DisplayName("유출된 토큰이 끝없이 연장되지 않도록, 연장한 토큰의 exp는 auth_time + 1일을 넘지 않고 maxAge는 그때까지 남은 시간이다")
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
    @DisplayName("이미 절대 기한까지 연장된 토큰은 새 exp가 늦지 않으므로 연장하지 않는다")
    void tokenAtAbsoluteLimitIsNotRenewed() {
        Instant authTime = NOW.minus(Duration.ofHours(23)).minus(Duration.ofMinutes(50));
        String token = TestJwts.of(USER_ID, authTime, authTime.plus(Duration.ofDays(1)));

        assertThat(tokenService.renew(token)).isEmpty();
        assertThat(tokenService.parse(token)).contains(DEFAULT_SUBJECT);
    }

    @Test
    @DisplayName("방금 발급한 토큰은 연장하지 않는다")
    void justIssuedTokenIsNotRenewed() {
        assertThat(tokenService.renew(tokenService.issue(SUBJECT).value())).isEmpty();
    }

    private static long authTimeOf(Jwt jwt) {
        Object value = jwt.getClaims().get(TestJwts.AUTH_TIME);
        assertThat(value).as("auth_time은 OIDC 표준대로 epoch 초 숫자다").isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }

    private static int versionOf(Jwt jwt) {
        Object value = jwt.getClaims().get(TestJwts.VERSION);
        assertThat(value).as("ver는 정수 숫자다").isInstanceOf(Number.class);
        return ((Number) value).intValue();
    }
}
