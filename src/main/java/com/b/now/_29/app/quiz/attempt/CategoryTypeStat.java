package com.b.now._29.app.quiz.attempt;

import com.b.now._29.app.quiz.question.QuestionType;

/** 카테고리와 문제 유형별 집계 한 행. */
public record CategoryTypeStat(
        String slug, String name, int sortOrder, QuestionType type, long solvedQuestions, long correctQuestions) {}
