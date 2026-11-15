package com.b.now._29.app.quiz.admin;

public class CategoryAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryAccessDeniedException() {
        super("담당하지 않는 카테고리입니다.");
    }
}
