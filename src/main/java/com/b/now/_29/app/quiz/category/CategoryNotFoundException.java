package com.b.now._29.app.quiz.category;

public class CategoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryNotFoundException(String slug) {
        super("카테고리를 찾을 수 없습니다: " + slug);
    }
}
