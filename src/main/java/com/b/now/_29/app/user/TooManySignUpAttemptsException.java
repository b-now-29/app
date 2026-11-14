package com.b.now._29.app.user;

import java.time.Duration;

public class TooManySignUpAttemptsException extends TooManyAttemptsException {

    public TooManySignUpAttemptsException(Duration retryAfter) {
        super("가입 시도가 너무 많습니다. 잠시 뒤에 다시 시도해 주세요.", retryAfter);
    }
}
