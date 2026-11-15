package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;
import java.util.Set;

/** 요청한 관리자가 다룰 수 있는 범위. OWNER는 모든 카테고리, 카테고리 관리자는 담당 카테고리만 다룬다. */
public record AdminScope(boolean isOwner, Set<Long> categoryIds) {

    public AdminScope {
        categoryIds = Set.copyOf(categoryIds);
    }

    public static AdminScope owner() {
        return new AdminScope(true, Set.of());
    }

    public static AdminScope categories(Set<Long> categoryIds) {
        return new AdminScope(false, categoryIds);
    }

    public boolean canManage(Category category) {
        return isOwner || categoryIds.contains(category.getId());
    }

    public void requireManage(Category category) {
        if (!canManage(category)) {
            throw new CategoryAccessDeniedException();
        }
    }
}
