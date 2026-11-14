package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.user.AttemptLimiter;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운영 시도 제한 수치 중 비밀번호 변경 제한 (docs/features/password-change.md R7, D2)을 확인한다. */
class AuthThrottleConfigTest {

    private static final int PASSWORD_CHANGE_MAX = 5;
    private static final Duration PASSWORD_CHANGE_WINDOW = Duration.ofMinutes(15);

    @Test
    @DisplayName("비밀번호 변경은 같은 키로 5번까지 되고 6번째는 막히며, 남은 시간은 15분 이내다")
    void passwordChangeAllowsFiveAttemptsPerFifteenMinutes() {
        AttemptLimiter<Long> limiter = new AuthThrottleConfig().authLimiters().passwordChange();

        for (int i = 0; i < PASSWORD_CHANGE_MAX; i++) {
            assertThat(limiter.tryAcquire(1L)).as("%d번째 시도", i + 1).isTrue();
        }

        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.retryAfter(1L))
                .isLessThanOrEqualTo(PASSWORD_CHANGE_WINDOW)
                .isGreaterThan(PASSWORD_CHANGE_WINDOW.minusMinutes(1));
    }

    @Test
    @DisplayName("비밀번호 변경 제한은 회원 ID(Long) 키별이라 한 회원이 막혀도 다른 회원은 된다 (D14)")
    void passwordChangeLimitIsPerKey() {
        AttemptLimiter<Long> limiter = new AuthThrottleConfig().authLimiters().passwordChange();
        for (int i = 0; i < PASSWORD_CHANGE_MAX; i++) {
            limiter.tryAcquire(1L);
        }

        assertThat(limiter.tryAcquire(2L)).isTrue();
    }

    @Test
    @DisplayName("비밀번호 변경 제한은 로그인·가입 제한과 따로 센다")
    void passwordChangeLimiterIsSeparate() {
        var limiters = new AuthThrottleConfig().authLimiters();

        assertThat(limiters.passwordChange())
                .isNotSameAs(limiters.loginPair())
                .isNotSameAs(limiters.loginIp())
                .isNotSameAs(limiters.signUpIp());
    }
}
