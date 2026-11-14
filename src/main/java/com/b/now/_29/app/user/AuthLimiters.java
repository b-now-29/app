package com.b.now._29.app.user;

/**
 * 인증 관련 시도 제한 묶음. 키 형식은 loginPair는 {@code 정규화한 이메일|ip}, passwordChange는 {@code 회원 ID}, 나머지는 {@code ip}다.
 */
public record AuthLimiters(
        AttemptLimiter<String> loginPair,
        AttemptLimiter<String> loginIp,
        AttemptLimiter<String> signUpIp,
        AttemptLimiter<Long> passwordChange) {}
