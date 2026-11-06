package com.b.now._29.app.quiz.attempt;

import com.b.now._29.app.quiz.question.QuestionType;

public record TypeStatResponse(QuestionType type, long solvedQuestions, long correctQuestions) {

    static TypeStatResponse from(CategoryTypeStat stat) {
        return new TypeStatResponse(stat.type(), stat.solvedQuestions(), stat.correctQuestions());
    }
}
