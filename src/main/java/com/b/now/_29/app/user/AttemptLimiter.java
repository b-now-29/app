package com.b.now._29.app.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 키별 시도 횟수를 첫 기록부터 고정 길이의 창으로 센다 (fixed window). 횟수는 메모리에만 둔다. 확인과 증가는 {@code compute} 한 번 안에서
 * 원자적으로 한다 (D13).
 */
public class AttemptLimiter<K> {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {}

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<K, Window> windows = new ConcurrentHashMap<>();

    public AttemptLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** 한도 안이면 횟수를 1 늘리고 true. 막혀 있으면 세지 않고 false. */
    public boolean tryAcquire(K key) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> !isActive(w, now));
        }
        boolean[] acquired = {false};
        windows.compute(key, (k, current) -> {
            if (current == null || !isActive(current, now)) {
                acquired[0] = true;
                return new Window(now, 1);
            }
            if (current.count() >= maxAttempts) {
                return current;
            }
            acquired[0] = true;
            return new Window(current.start(), current.count() + 1);
        });
        return acquired[0];
    }

    /** 한도 안이면 횟수를 1 늘리고, 막혀 있으면 세지 않고 남은 시간으로 만든 예외를 던진다 (D14). */
    public void acquireOrThrow(K key, Function<Duration, ? extends RuntimeException> exceptionFor) {
        if (!tryAcquire(key)) {
            throw exceptionFor.apply(retryAfter(key));
        }
    }

    /** 살아 있는 창의 횟수를 1 줄인다 (0 미만 안 됨). */
    public void release(K key) {
        Instant now = clock.instant();
        windows.computeIfPresent(key, (k, current) -> {
            if (!isActive(current, now) || current.count() == 0) {
                return current;
            }
            return new Window(current.start(), current.count() - 1);
        });
    }

    public void reset(K key) {
        windows.remove(key);
    }

    /** 차단 중이면 창이 끝날 때까지 남은 시간, 아니면 {@link Duration#ZERO}. */
    public Duration retryAfter(K key) {
        Window current = windows.get(key);
        Instant now = clock.instant();
        if (current == null || !isActive(current, now) || current.count() < maxAttempts) {
            return Duration.ZERO;
        }
        return Duration.between(now, current.start().plus(window));
    }

    private boolean isActive(Window w, Instant now) {
        return now.isBefore(w.start().plus(window));
    }
}
