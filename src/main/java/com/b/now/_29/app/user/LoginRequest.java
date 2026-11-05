package com.b.now._29.app.user;

import jakarta.validation.constraints.NotBlank;

/** 빈 값만 거른다. 가입 규칙에 맞지 않는 값은 인증 실패(401)로 처리한다 (D13). */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
