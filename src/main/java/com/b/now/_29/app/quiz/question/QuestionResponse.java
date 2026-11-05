package com.b.now._29.app.quiz.question;

import java.util.List;

/** 해설은 채점 전에 공개하지 않으므로 필드를 두지 않는다. */
public record QuestionResponse(long id, QuestionType type, String content, List<ChoiceResponse> choices) {

    static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getType(),
                question.getContent(),
                question.getChoices().stream().map(ChoiceResponse::from).toList());
    }
}
