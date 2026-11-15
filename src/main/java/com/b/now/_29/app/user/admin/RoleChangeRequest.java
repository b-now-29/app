package com.b.now._29.app.user.admin;

import com.b.now._29.app.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * categorySlugs를 빼면 빈 목록이다.
 */
public record RoleChangeRequest(@NotNull Role role, List<@NotBlank String> categorySlugs) {

    public RoleChangeRequest {
        categorySlugs = categorySlugs == null ? List.of() : categorySlugs;
    }

    /**
     * USER 또는 CATEGORY_ADMIN만 받는다. CATEGORY_ADMIN은 담당 카테고리가 1개 이상, USER는 없어야 한다 (R2, R9).
     */
    @AssertTrue
    public boolean isRoleAndCategoriesConsistent() {
        return role == null || role.isAssignable() && role.requiresCategories() != categorySlugs.isEmpty();
    }
}
