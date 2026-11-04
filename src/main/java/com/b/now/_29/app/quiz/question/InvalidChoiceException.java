package com.b.now._29.app.quiz.question;

/** 고른 보기가 그 문제의 보기가 아닐 때 던진다 (400). */
public class InvalidChoiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidChoiceException(long choiceId) {
        super("이 문제의 보기가 아닙니다: " + choiceId);
    }
}
