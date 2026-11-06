package com.b.now._29.app.quiz.category;

public class DuplicateCategorySlugException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateCategorySlugException(String slug) {
        super("이미 사용한 카테고리 식별자입니다 (삭제한 카테고리 포함): " + slug);
    }

    public DuplicateCategorySlugException(String slug, Throwable cause) {
        super("이미 사용한 카테고리 식별자입니다 (삭제한 카테고리 포함): " + slug, cause);
    }
}
