package com.b.now._29.app.quiz.wronganswer;

import com.b.now._29.app.quiz.question.QuestionType;
import java.time.Instant;

public record WrongAnswerQuestionResponse(long questionId, String content, QuestionType type, Instant attemptedAt) {

    static WrongAnswerQuestionResponse from(WrongAnswerRow row) {
        return new WrongAnswerQuestionResponse(row.questionId(), row.content(), row.type(), row.attemptedAt());
    }
}
