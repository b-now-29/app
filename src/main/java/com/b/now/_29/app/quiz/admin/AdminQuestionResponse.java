package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionType;
import java.util.List;

/** 관리자용 문제. 공개 API와 달리 모범 답안과 보기의 정답 여부를 포함한다. */
public record AdminQuestionResponse(
        long id,
        String categorySlug,
        QuestionType type,
        String content,
        String explanation,
        String modelAnswer,
        int sortOrder,
        List<AdminChoiceResponse> choices) {

    static AdminQuestionResponse from(Question question) {
        return new AdminQuestionResponse(
                question.getId(),
                question.getCategory().getSlug(),
                question.getType(),
                question.getContent(),
                question.getExplanation(),
                question.getModelAnswer(),
                question.getSortOrder(),
                question.getChoices().stream().map(AdminChoiceResponse::from).toList());
    }
}
