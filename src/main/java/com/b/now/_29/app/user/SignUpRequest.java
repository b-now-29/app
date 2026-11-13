package com.b.now._29.app.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email @Size(max = MAX_EMAIL_LENGTH) String email,

        @NotBlank @Size(min = 8) @MaxBytes(MAX_PASSWORD_BYTES)
        String password,

        @NicknameFormat String nickname) {

    /** BCrypt 입력 한도 (R3). 가입 검증과 로그인 사전 거절이 함께 쓴다 (D23). */
    public static final int MAX_PASSWORD_BYTES = 72;

    /** users.email 컬럼 길이 (D19). */
    public static final int MAX_EMAIL_LENGTH = 255;

    /** 앞뒤 공백이 있어도 형식 오류가 되지 않도록 검증 전에 지운다 (R2). */
    public SignUpRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
