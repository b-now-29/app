package com.b.now._29.app.quiz.question;

import java.util.List;

/** 문제를 만들거나 고칠 때 받는 값. 유형별 규칙 검증은 {@link Question#create}·{@link Question#update}가 한다. */
public record QuestionDraft(
        QuestionType type,
        String content,
        String explanation,
        String modelAnswer,
        int sortOrder,
        List<ChoiceDraft> choices) {

    public QuestionDraft {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
