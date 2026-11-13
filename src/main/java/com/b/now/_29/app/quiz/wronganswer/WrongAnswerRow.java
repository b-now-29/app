package com.b.now._29.app.quiz.wronganswer;

import com.b.now._29.app.quiz.question.QuestionType;
import java.time.Instant;

/** 오답 한 건의 평평한 조회 행. Service가 카테고리별로 묶는다. */
public record WrongAnswerRow(
        String slug, String name, long questionId, String content, QuestionType type, Instant attemptedAt) {}
