package com.b.now._29.app.user;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("이미 가입된 이메일입니다: " + email);
    }

    public DuplicateEmailException(String email, Throwable cause) {
        super("이미 가입된 이메일입니다: " + email, cause);
    }
}
