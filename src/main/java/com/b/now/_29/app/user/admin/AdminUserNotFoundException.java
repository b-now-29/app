package com.b.now._29.app.user.admin;

public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException(long id) {
        super("회원을 찾을 수 없습니다: " + id);
    }
}
