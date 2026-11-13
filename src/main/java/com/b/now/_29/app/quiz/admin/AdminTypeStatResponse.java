package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.QuestionType;

public record AdminTypeStatResponse(QuestionType type, long solved, long correct) {}
