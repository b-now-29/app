package com.b.now._29.app.quiz.question;

import jakarta.validation.constraints.NotNull;

public record AnswerRequest(@NotNull Long choiceId) {}
