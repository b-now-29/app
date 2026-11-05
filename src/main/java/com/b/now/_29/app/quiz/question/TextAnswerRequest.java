package com.b.now._29.app.quiz.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextAnswerRequest(@NotBlank @Size(max = 5000) String answer) {}
