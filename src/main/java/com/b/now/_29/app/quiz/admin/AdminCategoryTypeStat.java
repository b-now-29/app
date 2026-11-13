package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.QuestionType;

/** (카테고리, 유형)별 집계 한 행. 기록이 있는 조합만 나온다. */
public record AdminCategoryTypeStat(String slug, QuestionType type, long solved, long correct) {}
