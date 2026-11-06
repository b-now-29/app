package com.b.now._29.app.quiz.question;

/** 풀이 기록이 있는 문제의 유형을 바꾸려 했다. 내 기록 통계가 현재 유형으로 집계되므로 막는다 (D17). */
public class QuestionTypeChangeNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QuestionTypeChangeNotAllowedException(QuestionType from, QuestionType to) {
        super("풀이 기록이 있는 문제는 유형을 바꿀 수 없습니다.");
    }
}
