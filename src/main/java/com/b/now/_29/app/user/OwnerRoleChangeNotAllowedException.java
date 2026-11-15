package com.b.now._29.app.user;

public class OwnerRoleChangeNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OwnerRoleChangeNotAllowedException() {
        super("운영자의 역할은 바꿀 수 없습니다.");
    }
}
