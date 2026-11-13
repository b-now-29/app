package com.b.now._29.app.user;

/** 닉네임 규칙 위반. 메시지에 입력값을 담지 않는다. */
public class InvalidNicknameException extends RuntimeException {

    public InvalidNicknameException(String message) {
        super(message);
    }
}
