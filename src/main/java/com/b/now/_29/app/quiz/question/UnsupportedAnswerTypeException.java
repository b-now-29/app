package com.b.now._29.app.quiz.question;

/** 문제 유형이 지원하지 않는 방식으로 답을 제출했을 때 던진다 (400). */
public class UnsupportedAnswerTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedAnswerTypeException(QuestionType type) {
        super("이 유형의 문제에는 사용할 수 없습니다: " + type);
    }
}
