package com.b.now._29.app.user;

public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("현재 비밀번호가 맞지 않습니다.");
    }
}
