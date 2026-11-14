package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.b.now._29.app.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 로그인 보안 강화 (docs/features/login-hardening.md R1, R2, R5, R9, R12, D6, D13)에서 구현할 것:
 *
 * <ul>
 *   <li>{@code AttemptLimiter(int maxAttempts, Duration window, Clock clock)} — Spring에 의존하지 않는 순수 클래스
 *   <li>{@code boolean tryAcquire(String key)} — 한 번의 원자적 갱신 안에서, 살아 있는 창의 횟수가 한도 이상이면 false (세지
 *       않음), 아니면 횟수 + 1 후 true. 창이 없거나 끝났으면 지금부터 새 창 (fixed window, D6)
 *   <li>{@code void release(String key)} — 살아 있는 창의 횟수를 1 줄인다 (0 미만 안 됨). 창이 없거나 끝났으면 아무것도 안 함
 *   <li>{@code void reset(String key)}
 *   <li>{@code Duration retryAfter(String key)} — 차단 중이면 창이 끝날 때까지 남은 시간, 아니면 {@code Duration.ZERO}
 *   <li>맵이 10,000개를 넘으면 창이 끝난 항목을 지운다 (D7, D13)
 * </ul>
 */
class AttemptLimiterTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration JUST = Duration.ofMillis(1);
    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final String KEY = "user@example.com|key-a";

    private MutableClock clock;
    private AttemptLimiter<String> limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        limiter = new AttemptLimiter<>(MAX_ATTEMPTS, WINDOW, clock);
    }

    // acquireOrThrow: 한도 확인과 차단 예외를 한 번에 (password-change D14)

    /** 차단 예외가 받은 남은 시간을 그대로 꺼내 보려고 쓰는 예외. */
    private static final class Blocked extends RuntimeException {

        private final transient Duration retryAfter;

        Blocked(Duration retryAfter) {
            super("blocked");
            this.retryAfter = retryAfter;
        }
    }

    @Test
    @DisplayName("acquireOrThrow는 한도 안이면 예외 없이 통과하고 횟수를 1 늘린다 (password-change D14)")
    void acquireOrThrowPassesWithinLimitAndCounts() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThatCode(() -> limiter.acquireOrThrow(KEY, Blocked::new))
                    .as("%d번째 시도", i + 1)
                    .doesNotThrowAnyException();
        }

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("acquireOrThrow는 한도를 넘으면 받은 함수가 만든 예외를 창이 끝날 때까지 남은 시간과 함께 던진다 (password-change D14)")
    void acquireOrThrowThrowsWithRetryAfterWhenBlocked() {
        limiter.tryAcquire(KEY);
        clock.advance(Duration.ofMinutes(4));
        acquireTimes(KEY, MAX_ATTEMPTS - 1);

        Throwable thrown = catchThrowable(() -> limiter.acquireOrThrow(KEY, Blocked::new));

        assertThat(thrown).isInstanceOf(Blocked.class);
        assertThat(((Blocked) thrown).retryAfter).isEqualTo(WINDOW.minus(Duration.ofMinutes(4)));
    }

    @Test
    @DisplayName("acquireOrThrow로 막힌 시도는 세지 않는다: release 한 번이면 다시 한 번 통과한다 (password-change D14)")
    void blockedAcquireOrThrowIsNotCounted() {
        acquireTimes(KEY, MAX_ATTEMPTS);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limiter.acquireOrThrow(KEY, Blocked::new)).isInstanceOf(Blocked.class);
        }

        limiter.release(KEY);

        assertThatCode(() -> limiter.acquireOrThrow(KEY, Blocked::new)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquireOrThrow(KEY, Blocked::new)).isInstanceOf(Blocked.class);
    }

    @Test
    @DisplayName("acquireOrThrow는 한도 안이면 예외 함수를 부르지 않는다 (password-change D14)")
    void acquireOrThrowDoesNotCreateExceptionWithinLimit() {
        AtomicInteger created = new AtomicInteger();

        limiter.acquireOrThrow(KEY, retryAfter -> {
            created.incrementAndGet();
            return new Blocked(retryAfter);
        });

        assertThat(created).hasValue(0);
    }

    @Test
    @DisplayName("키 타입은 제네릭이라 회원 ID(Long)를 키로 쓸 수 있고, 같은 값의 Long은 같은 키다 (password-change D14)")
    void longKeys() {
        AttemptLimiter<Long> byUserId = new AttemptLimiter<>(MAX_ATTEMPTS, WINDOW, clock);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // 캐시 범위 밖의 값이라 매번 다른 Long 객체다
            assertThat(byUserId.tryAcquire(Long.valueOf(100_000L))).isTrue();
        }

        assertThat(byUserId.tryAcquire(100_000L)).isFalse();
        assertThat(byUserId.tryAcquire(100_001L)).isTrue();

        byUserId.reset(100_000L);
        assertThat(byUserId.tryAcquire(100_000L)).isTrue();
    }

    @Test
    @DisplayName("한도만큼은 true이고, 한도에 닿은 다음 시도부터 false다 (R1, D13)")
    void acquiresUpToLimit() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(limiter.tryAcquire(KEY)).as("%d번째 시도", i + 1).isTrue();
        }

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("기록이 창 안에 흩어져 있어도 첫 시도부터의 창 안이면 함께 센다 (D6)")
    void attemptsSpreadInsideWindowAreCounted() {
        Duration step = WINDOW.minus(JUST).dividedBy(MAX_ATTEMPTS - 1);
        limiter.tryAcquire(KEY);
        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            clock.advance(step);
            limiter.tryAcquire(KEY);
        }

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("거부된 시도는 세지 않는다: 하나를 release하면 다시 정확히 한 번 허용된다 (R5, D13)")
    void rejectedAttemptsAreNotCounted() {
        acquireTimes(KEY, MAX_ATTEMPTS);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(KEY)).isFalse();
        }

        limiter.release(KEY);

        assertThat(limiter.tryAcquire(KEY)).isTrue();
        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("막힌 뒤에도 첫 시도부터 창이 끝나기 직전까지는 계속 막는다 (R1, D6)")
    void staysBlockedUntilWindowEnds() {
        acquireTimes(KEY, MAX_ATTEMPTS);

        clock.advance(WINDOW.minus(JUST));

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("창은 첫 시도부터 잰다: 창이 끝나면 새 창으로 다시 한도만큼 허용한다 (D6, D13)")
    void newWindowAfterExpiry() {
        limiter.tryAcquire(KEY);
        clock.advance(WINDOW.minus(JUST));
        acquireTimes(KEY, MAX_ATTEMPTS - 1);
        assertThat(limiter.tryAcquire(KEY)).isFalse();

        clock.advance(JUST.multipliedBy(2));

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(limiter.tryAcquire(KEY)).as("새 창의 %d번째 시도", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("release는 살아 있는 창의 횟수를 하나 줄인다 (R13, D13)")
    void releaseGivesBackOne() {
        acquireTimes(KEY, MAX_ATTEMPTS);

        limiter.release(KEY);
        limiter.release(KEY);

        assertThat(limiter.tryAcquire(KEY)).isTrue();
        assertThat(limiter.tryAcquire(KEY)).isTrue();
        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("release는 횟수를 0 아래로 내리지 않는다 (D13)")
    void releaseDoesNotGoBelowZero() {
        limiter.tryAcquire(KEY);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            limiter.release(KEY);
        }

        acquireTimes(KEY, MAX_ATTEMPTS);

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("기록이 없는 키를 release해도 오류가 없고 남는 몫을 만들지 않는다 (D13)")
    void releaseUnknownKeyDoesNothing() {
        limiter.release(KEY);
        limiter.release(KEY);

        acquireTimes(KEY, MAX_ATTEMPTS);

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("창이 끝난 뒤의 release는 무시한다: 새 창의 횟수에 영향을 주지 않는다 (D13)")
    void releaseAfterWindowIsIgnored() {
        acquireTimes(KEY, MAX_ATTEMPTS);
        clock.advance(WINDOW.plus(JUST));

        limiter.release(KEY);
        limiter.release(KEY);

        acquireTimes(KEY, MAX_ATTEMPTS);
        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("reset하면 그 키의 횟수가 지워져 다시 한도만큼 허용한다 (R4)")
    void resetClearsCount() {
        acquireTimes(KEY, MAX_ATTEMPTS);

        limiter.reset(KEY);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(limiter.tryAcquire(KEY)).isTrue();
        }
        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    @DisplayName("기록이 없는 키를 reset해도 오류가 없다")
    void resetUnknownKey() {
        limiter.reset(KEY);

        assertThat(limiter.tryAcquire(KEY)).isTrue();
    }

    @Test
    @DisplayName("키끼리 횟수가 섞이지 않는다 (D11)")
    void keysAreIndependent() {
        String other = "user@example.com|key-b";
        acquireTimes(KEY, MAX_ATTEMPTS);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(limiter.tryAcquire(other)).isTrue();
        }
        assertThat(limiter.tryAcquire(KEY)).isFalse();

        limiter.reset(KEY);
        assertThat(limiter.tryAcquire(KEY)).isTrue();
        assertThat(limiter.tryAcquire(other)).isFalse();
    }

    @Test
    @DisplayName("retryAfter: 기록이 없거나 한도 미만이면 ZERO다 (D13)")
    void retryAfterIsZeroWhenNotBlocked() {
        assertThat(limiter.retryAfter(KEY)).isEqualTo(Duration.ZERO);

        acquireTimes(KEY, MAX_ATTEMPTS - 1);

        assertThat(limiter.retryAfter(KEY)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("retryAfter: 차단 중이면 첫 시도부터 창이 끝날 때까지 남은 시간이다 (R15, D13)")
    void retryAfterIsRemainingWindowWhenBlocked() {
        limiter.tryAcquire(KEY);
        clock.advance(Duration.ofMinutes(4));
        acquireTimes(KEY, MAX_ATTEMPTS - 1);

        assertThat(limiter.retryAfter(KEY)).isEqualTo(WINDOW.minus(Duration.ofMinutes(4)));

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.retryAfter(KEY)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("retryAfter: 창이 끝났으면 ZERO다 (D13)")
    void retryAfterIsZeroAfterWindow() {
        acquireTimes(KEY, MAX_ATTEMPTS);

        clock.advance(WINDOW.plus(JUST));

        assertThat(limiter.retryAfter(KEY)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("기록이 10,000개를 넘어 정리해도 창이 진행 중인 항목은 남는다 (D7, D13)")
    void cleanupKeepsActiveWindows() {
        for (int i = 0; i < CLEANUP_THRESHOLD; i++) {
            limiter.tryAcquire("expired-" + i);
        }
        clock.advance(WINDOW.dividedBy(2));
        acquireTimes(KEY, MAX_ATTEMPTS);
        String almostBlocked = "almost";
        acquireTimes(almostBlocked, MAX_ATTEMPTS - 1);
        // expired-* 는 창이 끝났고, KEY와 almost는 창의 절반이 남았다
        clock.advance(WINDOW.dividedBy(2).plus(JUST));

        for (int i = 0; i < CLEANUP_THRESHOLD; i++) {
            limiter.tryAcquire("fresh-" + i);
        }

        assertThat(limiter.tryAcquire(KEY)).isFalse();
        assertThat(limiter.tryAcquire(almostBlocked)).isTrue();
        assertThat(limiter.tryAcquire(almostBlocked)).isFalse();
        assertThat(limiter.tryAcquire("expired-0")).isTrue();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("같은 키로 여러 스레드가 동시에 tryAcquire해도 정확히 한도만큼만 true다 (R12, D13)")
    void concurrentAcquireNeverExceedsLimit() throws Exception {
        int threads = 32;
        int rounds = 50;
        // close()는 제출한 작업이 끝날 때까지 기다린다. 작업의 대기는 모두 시간 제한이 있어 (start.await 10초) close()가
        // 끝없이 기다리지 않는다. @Timeout이 테스트 스레드를 interrupt하면 close()는 shutdownNow로 작업을 interrupt한다
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int round = 0; round < rounds; round++) {
                String key = "concurrent-" + round;
                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    results.add(executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("출발 신호를 받지 못함");
                        }
                        return limiter.tryAcquire(key);
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                int acquired = 0;
                for (Future<Boolean> result : results) {
                    if (result.get(10, TimeUnit.SECONDS)) {
                        acquired++;
                    }
                }
                assertThat(acquired).as("round %d", round).isEqualTo(MAX_ATTEMPTS);
            }
        }
    }

    private void acquireTimes(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.tryAcquire(key);
        }
    }
}
