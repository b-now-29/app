package com.b.now._29.app.user;

public class LastAdminDemotionNotAllowedException extends RuntimeException {

    public LastAdminDemotionNotAllowedException() {
        super("마지막 관리자의 권한은 해제할 수 없습니다.");
    }
}
