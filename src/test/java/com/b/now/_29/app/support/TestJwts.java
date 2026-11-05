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

/** 구현과 상관없이 정상·만료·위조 토큰을 만든다. 키는 비밀값의 UTF-8 바이트를 그대로 쓴 HS256이다 (D2). */
public final class TestJwts {

    public static final String SECRET = "test-jwt-secret-with-at-least-32-bytes!";

    private static final String OTHER_SECRET = "other-jwt-secret-with-at-least-32-bytes!";

    // 디코더의 기본 허용 오차(60초)보다 충분히 크게 잡아야 만료로 판정된다
    private static final Duration FAR = Duration.ofHours(1);

    private TestJwts() {}

    public static SecretKey key() {
        return keyOf(SECRET);
    }

    public static String valid(long userId) {
        return sign(SECRET, String.valueOf(userId), Instant.now().plus(FAR));
    }

    public static String expired(long userId) {
        return sign(SECRET, String.valueOf(userId), Instant.now().minus(FAR));
    }

    public static String signedWithOtherKey(long userId) {
        return sign(OTHER_SECRET, String.valueOf(userId), Instant.now().plus(FAR));
    }

    public static String withNonNumericSubject() {
        return sign(SECRET, "not-a-number", Instant.now().plus(FAR));
    }

    /** 서명은 그대로 두고 본문의 사용자 id만 바꾼다. */
    public static String tampered(long userId, long otherUserId) {
        String[] parts = valid(userId).split("\\.");
        return parts[0] + "." + payload(otherUserId) + "." + parts[2];
    }

    /** {@code alg: none}으로 서명을 뺀 토큰. */
    public static String unsigned(long userId) {
        return base64Url("{\"alg\":\"none\"}") + "." + payload(userId) + ".";
    }

    private static String payload(long userId) {
        long expiresAt = Instant.now().plus(FAR).getEpochSecond();
        return base64Url("{\"sub\":\"" + userId + "\",\"exp\":" + expiresAt + "}");
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String secret, String subject, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(expiresAt.minus(Duration.ofHours(2)))
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(keyOf(secret)))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private static SecretKey keyOf(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
