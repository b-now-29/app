package com.b.now._29.app.quiz.question;

public record TextAnswerResponse(String modelAnswer, String explanation) {

    static TextAnswerResponse from(ModelAnswerResult result) {
        return new TextAnswerResponse(result.modelAnswer(), result.explanation());
    }
}
