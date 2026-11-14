package com.b.now._29.app.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 현재 비밀번호는 길이로 거르지 않는다. 틀린 비밀번호와 같은 400이 되고 시도로 세야 한다 (D6). */
public record PasswordChangeRequest(
        @NotNull String currentPassword,

        @NotBlank @Size(min = 8) @MaxBytes(PasswordRule.MAX_BYTES) @NoNul
        String newPassword) {}
