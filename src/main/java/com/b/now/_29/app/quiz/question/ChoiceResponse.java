package com.b.now._29.app.quiz.question;

/** 정답 여부는 채점 전에 공개하지 않으므로 필드를 두지 않는다. */
public record ChoiceResponse(long id, String content) {

    static ChoiceResponse from(Choice choice) {
        return new ChoiceResponse(choice.getId(), choice.getContent());
    }
}
