package com.b.now._29.app.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 구현과 상관없이 정상·만료·위조 토큰을 만든다. 키는 운영 설정과 같게 비밀값의 UTF-8 바이트를 그대로 쓴 HS256이다.
 *
 * <p>정상 토큰에는 {@code auth_time}(로그인 시각, epoch 초)과 {@code ver}(발급할 때 회원의 토큰 버전)이 있어야 한다. 버전을 받지 않는 메서드는
 * 새 회원의 버전인 {@link #DEFAULT_VERSION}을 넣는다. {@link #valid}는 남은 시간이 1시간이라 연장 대상(30분 미만)이 아니다.
 *
 * <p>{@code Instant now}를 받는 메서드는 고정 시계를 쓰는 단위 테스트용이다. 만료 검사가 시스템 시계가 아니라 주입한 시계를 따르는지 확인하기 위해서다. 받지 않는 메서드는 실제 지금을 기준으로 한다.
 */
public final class TestJwts {

    public static final String SECRET = "test-jwt-secret-with-at-least-32-bytes!";

    public static final String AUTH_TIME = "auth_time";

    public static final String VERSION = "ver";

    /** 가입한 뒤 로그아웃한 적 없는 회원의 토큰 버전. */
    public static final int DEFAULT_VERSION = 0;

    private static final String OTHER_SECRET = "other-jwt-secret-with-at-least-32-bytes!";

    // 만료 토큰은 요청이 느려도 확실히 만료되도록, 정상 토큰은 확실히 유효하도록 넉넉히 잡는다
    private static final Duration FAR = Duration.ofHours(1);

    // 연장 대상 토큰: 1시간 토큰을 받은 지 50분이 지나 남은 시간이 연장 기준(30분)보다 짧은 10분이다
    private static final Duration NEAR_EXPIRY_AGE = Duration.ofMinutes(50);
    private static final Duration NEAR_EXPIRY_REMAINING = Duration.ofMinutes(10);

    private TestJwts() {}

    public static SecretKey key() {
        return keyOf(SECRET);
    }

    public static String valid(long userId) {
        return valid(userId, Instant.now());
    }

    public static String valid(long userId, Instant now) {
        return withVersion(userId, DEFAULT_VERSION, now);
    }

    /** 정해진 토큰 버전을 담은 정상 토큰. */
    public static String withVersion(long userId, int version) {
        return withVersion(userId, version, Instant.now());
    }

    public static String withVersion(long userId, int version, Instant now) {
        return sign(SECRET, String.valueOf(userId), now.minus(FAR), now.plus(FAR), version);
    }

    /** 로그인 시각과 만료 시각을 정해 같은 키로 서명한다. */
    public static String of(long userId, Instant authTime, Instant expiresAt) {
        return of(userId, DEFAULT_VERSION, authTime, expiresAt);
    }

    public static String of(long userId, int version, Instant authTime, Instant expiresAt) {
        return sign(SECRET, String.valueOf(userId), authTime, expiresAt, version);
    }

    /** 로그인한 지 50분이 지나 남은 시간이 10분인 정상 토큰. 받아들이면 자동 연장 대상이다. */
    public static String nearExpiry(long userId) {
        return nearExpiry(userId, DEFAULT_VERSION);
    }

    public static String nearExpiry(long userId, int version) {
        Instant now = Instant.now();
        return of(userId, version, now.minus(NEAR_EXPIRY_AGE), now.plus(NEAR_EXPIRY_REMAINING));
    }

    /** {@link #nearExpiry}와 기한이 같고 {@code ver}만 없어, 버전을 확인하지 않고 연장하면 드러난다. */
    public static String nearExpiryWithoutVersion(long userId) {
        Instant now = Instant.now();
        return withoutVersion(userId, now.minus(NEAR_EXPIRY_AGE), now.plus(NEAR_EXPIRY_REMAINING));
    }

    /** 토큰 무효화 배포 전에 발급된 토큰처럼 {@code ver}가 없다. 서명·기한·{@code auth_time}은 정상이다. */
    public static String withoutVersion(long userId) {
        return withoutVersion(userId, Instant.now());
    }

    public static String withoutVersion(long userId, Instant now) {
        return withoutVersion(userId, now.minus(FAR), now.plus(FAR));
    }

    public static String withoutVersion(long userId, Instant authTime, Instant expiresAt) {
        return sign(SECRET, String.valueOf(userId), authTime, expiresAt, null);
    }

    /** 자동 연장을 도입하기 전에 발급된 7일 토큰처럼 {@code auth_time}이 없다. */
    public static String withoutAuthTime(long userId) {
        return withoutAuthTime(userId, Instant.now());
    }

    public static String withoutAuthTime(long userId, Instant now) {
        return sign(SECRET, String.valueOf(userId), null, now.plus(Duration.ofDays(7)), DEFAULT_VERSION);
    }

    public static String expired(long userId) {
        return expired(userId, Instant.now());
    }

    public static String expired(long userId, Instant now) {
        return sign(SECRET, String.valueOf(userId), now.minus(FAR.multipliedBy(2)), now.minus(FAR), DEFAULT_VERSION);
    }

    public static String signedWithOtherKey(long userId) {
        return signedWithOtherKey(userId, Instant.now());
    }

    public static String signedWithOtherKey(long userId, Instant now) {
        return sign(OTHER_SECRET, String.valueOf(userId), now.minus(FAR), now.plus(FAR), DEFAULT_VERSION);
    }

    public static String withNonNumericSubject() {
        return withNonNumericSubject(Instant.now());
    }

    public static String withNonNumericSubject(Instant now) {
        return sign(SECRET, "not-a-number", now.minus(FAR), now.plus(FAR), DEFAULT_VERSION);
    }

    /**
     * 헤더 {@code typ}만 바꾼 토큰. 서명·{@code auth_time}·{@code ver}는 정상이고 남은 시간이 10분이라, 다른 종류의 JWT를 잘못 받아들이면
     * 연장까지 되는지도 함께 드러난다.
     */
    public static String withType(long userId, String type) {
        return withType(userId, type, Instant.now());
    }

    public static String withType(long userId, String type, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now.minus(NEAR_EXPIRY_AGE))
                .expiresAt(now.plus(NEAR_EXPIRY_REMAINING))
                .claim(AUTH_TIME, now.minus(NEAR_EXPIRY_AGE).getEpochSecond())
                .claim(VERSION, DEFAULT_VERSION)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type(type).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key()))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    /** 서명은 그대로 두고 본문의 사용자 id만 바꾼다. */
    public static String tampered(long userId, long otherUserId) {
        return tampered(userId, otherUserId, Instant.now());
    }

    public static String tampered(long userId, long otherUserId, Instant now) {
        String[] parts = valid(userId, now).split("\\.");
        return parts[0] + "." + payload(otherUserId, now) + "." + parts[2];
    }

    /** {@code alg: none}으로 서명을 뺀 토큰. */
    public static String unsigned(long userId) {
        return unsigned(userId, Instant.now());
    }

    public static String unsigned(long userId, Instant now) {
        return base64Url("{\"alg\":\"none\"}") + "." + payload(userId, now) + ".";
    }

    private static String payload(long userId, Instant now) {
        return base64Url("{\"sub\":\"%d\",\"exp\":%d,\"auth_time\":%d,\"ver\":%d}"
                .formatted(
                        userId, now.plus(FAR).getEpochSecond(), now.minus(FAR).getEpochSecond(), DEFAULT_VERSION));
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code authTime}이 null이면 {@code auth_time}을, {@code version}이 null이면 {@code ver}를 넣지 않는다. */
    private static String sign(String secret, String subject, Instant authTime, Instant expiresAt, Integer version) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(authTime == null ? expiresAt.minus(Duration.ofDays(7)) : authTime)
                .expiresAt(expiresAt);
        if (authTime != null) {
            claims.claim(AUTH_TIME, authTime.getEpochSecond());
        }
        if (version != null) {
            claims.claim(VERSION, version);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(keyOf(secret)))
                .encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
    }

    private static SecretKey keyOf(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
