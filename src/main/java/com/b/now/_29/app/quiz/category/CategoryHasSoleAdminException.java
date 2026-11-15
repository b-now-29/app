package com.b.now._29.app.quiz.category;

public class CategoryHasSoleAdminException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryHasSoleAdminException() {
        super("이 카테고리만 담당하는 관리자가 있어 삭제할 수 없습니다.");
    }
}
