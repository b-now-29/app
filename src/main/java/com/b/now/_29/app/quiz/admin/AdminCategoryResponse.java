package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;

/** 관리자용 카테고리. questionCount는 삭제하지 않은 문제 수다. */
public record AdminCategoryResponse(String slug, String name, String description, int sortOrder, long questionCount) {

    static AdminCategoryResponse from(Category category, long questionCount) {
        return new AdminCategoryResponse(
                category.getSlug(),
                category.getName(),
                category.getDescription(),
                category.getSortOrder(),
                questionCount);
    }
}
