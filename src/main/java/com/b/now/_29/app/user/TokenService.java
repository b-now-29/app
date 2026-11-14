package com.b.now._29.app.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

    private static final String AUTH_TIME = "auth_time";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.clock = clock;
    }

    public IssuedToken issue(long userId) {
        Instant now = now();
        return encode(userId, now, now, now.plus(ValidToken.LIFETIME));
    }

    /** 잘못된 토큰은 예외 없이 빈 값이다. 비회원으로 처리하기 위해서다. */
    public Optional<Long> parseUserId(String token) {
        return validate(token).map(ValidToken::userId);
    }

    /** 연장할 때만 새 토큰을 준다. 잘못된 토큰이나 연장이 필요 없는 토큰은 빈 값이다. */
    public Optional<IssuedToken> renew(String token) {
        Instant now = now();
        return validate(token).flatMap(current -> current.renewedExpiry(now)
                .map(expiresAt -> encode(current.userId(), current.authTime(), now, expiresAt)));
    }

    private IssuedToken encode(long userId, Instant authTime, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AUTH_TIME, authTime.getEpochSecond())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, Duration.between(issuedAt, expiresAt));
    }

    private Optional<ValidToken> validate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Object authTimeClaim = jwt.getClaims().get(AUTH_TIME);
            Instant expiresAt = jwt.getExpiresAt();
            if (!(authTimeClaim instanceof Number authTimeSeconds) || expiresAt == null) {
                return Optional.empty();
            }
            Instant authTime = Instant.ofEpochSecond(authTimeSeconds.longValue());
            ValidToken valid = new ValidToken(Long.parseLong(jwt.getSubject()), authTime, expiresAt);
            Instant now = now();
            if (!valid.isUnexpired(now) || valid.isPastAbsoluteLimit(now)) {
                return Optional.empty();
            }
            return Optional.of(valid);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
