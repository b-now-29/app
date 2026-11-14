package com.b.now._29.app.user;

import java.time.Duration;

public class TooManyPasswordChangeAttemptsException extends TooManyAttemptsException {

    public TooManyPasswordChangeAttemptsException(Duration retryAfter) {
        super("비밀번호 변경 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
    }
}
