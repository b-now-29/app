package com.b.now._29.app.quiz.question;

/** 답을 제출한 뒤에 공개하는 모범 답안과 해설. 해설은 없을 수 있다. */
public record ModelAnswerResult(String modelAnswer, String explanation) {}
