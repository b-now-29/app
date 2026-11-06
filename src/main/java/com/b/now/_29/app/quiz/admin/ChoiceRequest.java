package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.ChoiceDraft;
import jakarta.validation.constraints.NotBlank;

public record ChoiceRequest(@NotBlank String content, boolean correct) {

    ChoiceDraft toDraft() {
        return new ChoiceDraft(content, correct);
    }
}
