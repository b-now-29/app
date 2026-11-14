package com.b.now._29.app.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 서명·만료·{@code auth_time}·{@code ver}를 통과한 토큰의 내용. */
record ValidToken(TokenSubject subject, Instant authTime, Instant expiresAt) {

    static final Duration LIFETIME = Duration.ofHours(1);
    static final Duration ABSOLUTE_LIFETIME = Duration.ofDays(1);

    private static final Duration RENEW_THRESHOLD = Duration.ofMinutes(30);

    /** {@code exp} 시각부터는 거부한다. Spring의 시간 검증기는 {@code exp}가 지금과 같은 토큰을 통과시킨다. */
    boolean isUnexpired(Instant now) {
        return expiresAt.isAfter(now);
    }

    /** 처음 로그인한 시각에서 정해진 기한이 지났는지. */
    boolean isPastAbsoluteLimit(Instant now) {
        return absoluteLimit().isBefore(now);
    }

    private Instant absoluteLimit() {
        return authTime.plus(ABSOLUTE_LIFETIME);
    }

    /** 연장해야 할 때만 새 exp를 준다. 남은 시간이 충분하거나 새 exp가 지금 exp보다 늦지 않으면 빈 값이다. */
    Optional<Instant> renewedExpiry(Instant now) {
        if (Duration.between(now, expiresAt).compareTo(RENEW_THRESHOLD) >= 0) {
            return Optional.empty();
        }
        Instant absoluteLimit = absoluteLimit();
        Instant lifetimeLimit = now.plus(LIFETIME);
        Instant newExpiry = lifetimeLimit.isBefore(absoluteLimit) ? lifetimeLimit : absoluteLimit;
        return newExpiry.isAfter(expiresAt) ? Optional.of(newExpiry) : Optional.empty();
    }
}
