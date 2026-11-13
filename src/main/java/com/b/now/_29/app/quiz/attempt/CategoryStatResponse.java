package com.b.now._29.app.quiz.attempt;

import java.util.List;

public record CategoryStatResponse(String slug, String name, List<TypeStatResponse> types) {}
