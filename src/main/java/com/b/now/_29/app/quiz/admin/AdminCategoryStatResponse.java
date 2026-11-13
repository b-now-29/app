package com.b.now._29.app.quiz.admin;

import java.util.List;

public record AdminCategoryStatResponse(String slug, String name, List<AdminTypeStatResponse> types) {}
