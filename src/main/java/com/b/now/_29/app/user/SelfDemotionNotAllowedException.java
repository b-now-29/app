package com.b.now._29.app.user;

public class SelfDemotionNotAllowedException extends RuntimeException {

    public SelfDemotionNotAllowedException(long userId) {
        super("자기 자신의 관리자 권한은 해제할 수 없습니다: " + userId);
    }
}
