package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.Choice;

/** 관리자용 보기. 공개 API와 달리 정답 여부를 포함한다. */
public record AdminChoiceResponse(long id, String content, boolean correct) {

    static AdminChoiceResponse from(Choice choice) {
        return new AdminChoiceResponse(choice.getId(), choice.getContent(), choice.isCorrect());
    }
}
