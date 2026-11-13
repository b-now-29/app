package com.b.now._29.app.quiz.question;

public record AnswerResponse(boolean correct, long correctChoiceId, String explanation, boolean recorded) {

    static AnswerResponse from(GradingResult result, boolean recorded) {
        return new AnswerResponse(result.correct(), result.correctChoiceId(), result.explanation(), recorded);
    }
}
