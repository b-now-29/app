package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.QuestionDraft;
import com.b.now._29.app.quiz.question.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 유형별 규칙 (보기 수, 정답 수, 모범 답안)은 {@code Question.create}·{@code update}가 검증한다. */
public record QuestionRequest(
        @NotBlank String categorySlug,
        @NotNull QuestionType type,
        @NotBlank String content,
        String explanation,
        String modelAnswer,
        int sortOrder,
        List<@NotNull @Valid ChoiceRequest> choices) {

    QuestionDraft toDraft() {
        List<ChoiceRequest> requested = choices == null ? List.of() : choices;
        return new QuestionDraft(
                type,
                content,
                explanation,
                modelAnswer,
                sortOrder,
                requested.stream().map(ChoiceRequest::toDraft).toList());
    }
}
