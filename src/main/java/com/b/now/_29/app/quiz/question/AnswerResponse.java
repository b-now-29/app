package com.b.now._29.app.quiz.question;

public record AnswerResponse(boolean correct, long correctChoiceId, String explanation) {

    static AnswerResponse from(GradingResult result) {
        return new AnswerResponse(result.correct(), result.correctChoiceId(), result.explanation());
    }
}
