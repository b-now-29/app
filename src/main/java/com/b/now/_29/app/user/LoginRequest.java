package com.b.now._29.app.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 빈 값과 이메일 길이만 거른다. 가입 규칙에 맞지 않는 나머지 값은 인증 실패(401)로 처리한다 (D13). */
public record LoginRequest(
        @NotBlank @Size(max = 255) String email, @NotBlank String password) {}
