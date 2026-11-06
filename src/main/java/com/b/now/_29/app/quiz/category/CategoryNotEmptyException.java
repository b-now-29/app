package com.b.now._29.app.quiz.category;

public class CategoryNotEmptyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryNotEmptyException(String slug) {
        super("문제가 남아 있는 카테고리는 삭제할 수 없습니다: " + slug);
    }
}
