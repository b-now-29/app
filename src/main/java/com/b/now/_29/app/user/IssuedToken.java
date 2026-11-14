package com.b.now._29.app.user;

import java.time.Duration;

/** 발급한 토큰과, 지금부터 그 토큰의 exp까지 남은 시간 (쿠키 Max-Age). */
public record IssuedToken(String value, Duration maxAge) {}
