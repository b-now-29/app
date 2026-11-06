package com.b.now._29.app.quiz.question;

/** 문제 유형별 규칙을 어겼다. 메시지는 400 응답의 detail로 화면에 보여 준다. */
public class InvalidQuestionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidQuestionException(String message) {
        super(message);
    }
}
