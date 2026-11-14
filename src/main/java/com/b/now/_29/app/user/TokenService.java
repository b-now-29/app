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
    private static final String VERSION = "ver";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.clock = clock;
    }

    public IssuedToken issue(TokenSubject subject) {
        Instant now = now();
        return encode(subject, now, now, now.plus(ValidToken.LIFETIME));
    }

    /** 잘못된 토큰은 예외 없이 빈 값이다. 비회원으로 처리하기 위해서다. */
    public Optional<TokenSubject> parse(String token) {
        return validate(token).map(ValidToken::subject);
    }

    /** 연장할 때만 새 토큰을 준다. 잘못된 토큰이나 연장이 필요 없는 토큰은 빈 값이다. */
    public Optional<IssuedToken> renew(String token) {
        Instant now = now();
        return validate(token).flatMap(current -> current.renewedExpiry(now)
                .map(expiresAt -> encode(current.subject(), current.authTime(), now, expiresAt)));
    }

    private IssuedToken encode(TokenSubject subject, Instant authTime, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(subject.userId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AUTH_TIME, authTime.getEpochSecond())
                .claim(VERSION, subject.tokenVersion())
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
            Object versionClaim = jwt.getClaims().get(VERSION);
            if (!(authTimeClaim instanceof Number authTimeSeconds)
                    || !(versionClaim instanceof Number version)
                    || expiresAt == null) {
                return Optional.empty();
            }
            Instant authTime = Instant.ofEpochSecond(authTimeSeconds.longValue());
            TokenSubject subject = new TokenSubject(Long.parseLong(jwt.getSubject()), version.intValue());
            ValidToken valid = new ValidToken(subject, authTime, expiresAt);
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
