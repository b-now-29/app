package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.QuestionType;

public record AdminQuestionStatResponse(
        long id, QuestionType type, String content, long solvedUsers, long correctUsers) {}
