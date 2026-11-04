package com.b.now._29.app.quiz.category;

public record CategoryResponse(String slug, String name) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getSlug(), category.getName());
    }
}
