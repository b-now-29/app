package com.b.now._29.app.quiz.wronganswer;

import java.util.List;

public record WrongAnswerCategoryResponse(String slug, String name, List<WrongAnswerQuestionResponse> questions) {}
