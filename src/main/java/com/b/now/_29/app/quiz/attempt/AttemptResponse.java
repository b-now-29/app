package com.b.now._29.app.quiz.attempt;

import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionType;
import java.time.Instant;

public record AttemptResponse(
        long id,
        long questionId,
        String questionContent,
        QuestionType questionType,
        String categoryName,
        boolean correct,
        Instant attemptedAt) {

    static AttemptResponse from(QuestionAttempt attempt) {
        Question question = attempt.getQuestion();
        return new AttemptResponse(
                attempt.getId(),
                question.getId(),
                question.getContent(),
                question.getType(),
                question.getCategory().getName(),
                attempt.isCorrect(),
                attempt.getAttemptedAt());
    }
}
