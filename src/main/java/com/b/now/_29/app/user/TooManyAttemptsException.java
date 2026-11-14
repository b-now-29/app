package com.b.now._29.app.user;

import java.time.Duration;

/** 시도 제한에 걸려 429 + Retry-After로 응답하는 예외의 공통 부모. */
public abstract class TooManyAttemptsException extends RuntimeException {

    private final transient Duration retryAfter;

    protected TooManyAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
