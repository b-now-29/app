package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 역할은 USER, CATEGORY_ADMIN, OWNER 3가지이고 USER가 아니면 관리자다 (role-owner-category-admin R1). */
class RoleTest {

    @Test
    @DisplayName("역할은 USER, CATEGORY_ADMIN, OWNER 3가지이고 ADMIN은 없다 (R1)")
    void hasThreeRoles() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.CATEGORY_ADMIN, Role.OWNER);
    }

    @Test
    @DisplayName("USER는 관리자가 아니다 (R1)")
    void userIsNotStaff() {
        assertThat(Role.USER.isStaff()).isFalse();
    }

    @Test
    @DisplayName("CATEGORY_ADMIN과 OWNER는 관리자다 (R1, R8)")
    void adminsAreStaff() {
        assertThat(Role.CATEGORY_ADMIN.isStaff()).isTrue();
        assertThat(Role.OWNER.isStaff()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"USER, false", "CATEGORY_ADMIN, false", "OWNER, true"})
    @DisplayName("isOwner는 OWNER만 참이다 (R2, D14)")
    void isOwner(Role role, boolean expected) {
        assertThat(role.isOwner()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"USER, false", "CATEGORY_ADMIN, true", "OWNER, false"})
    @DisplayName("담당 카테고리가 있어야 하는 역할은 CATEGORY_ADMIN뿐이다 (R9, D9)")
    void requiresCategories(Role role, boolean expected) {
        assertThat(role.requiresCategories()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"USER, true", "CATEGORY_ADMIN, true", "OWNER, false"})
    @DisplayName("API로 줄 수 있는 역할은 USER와 CATEGORY_ADMIN이다. OWNER는 줄 수 없다 (R2)")
    void isAssignable(Role role, boolean expected) {
        assertThat(role.isAssignable()).isEqualTo(expected);
    }
}
