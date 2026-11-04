package com.b.now._29.app.quiz.question;

public class QuestionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QuestionNotFoundException(long questionId) {
        super("문제를 찾을 수 없습니다: " + questionId);
    }
}
