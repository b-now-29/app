package com.b.now._29.app.user;

import java.nio.charset.StandardCharsets;

/** 로그인·비밀번호 변경·검증 어노테이션이 함께 쓰는 비밀번호 바이트 규칙 (D6, D11, D13). */
public final class PasswordRule {

    /** BCrypt 입력 한도. */
    public static final int MAX_BYTES = 72;

    private PasswordRule() {}

    public static boolean exceedsMaxBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    /** PostgreSQL은 NUL 문자를 거부한다. */
    public static boolean containsNul(String password) {
        return password.indexOf('\0') >= 0;
    }
}
