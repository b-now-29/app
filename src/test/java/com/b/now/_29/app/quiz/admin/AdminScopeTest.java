package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.support.TestCategories;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요청한 관리자가 다룰 수 있는 범위 (role-owner-category-admin R4, R6, API 계약 {@code AdminScope}). */
class AdminScopeTest {

    private static final Category JAVA = TestCategories.withId(1L, "java");
    private static final Category SPRING = TestCategories.withId(2L, "spring");

    @Test
    @DisplayName("OWNER 범위는 모든 카테고리를 관리한다 (R4)")
    void ownerManagesEveryCategory() {
        AdminScope scope = AdminScope.owner();

        assertThat(scope.isOwner()).isTrue();
        assertThat(scope.canManage(JAVA)).isTrue();
        assertThat(scope.canManage(SPRING)).isTrue();
        assertThatCode(() -> scope.requireManage(SPRING)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("카테고리 관리자 범위는 담당 카테고리만 관리하고 OWNER가 아니다 (R4)")
    void categoryScopeManagesOnlyAssigned() {
        AdminScope scope = AdminScope.categories(Set.of(1L));

        assertThat(scope.isOwner()).isFalse();
        assertThat(scope.canManage(JAVA)).isTrue();
        assertThat(scope.canManage(SPRING)).isFalse();
        assertThatCode(() -> scope.requireManage(JAVA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("담당이 아닌 카테고리를 requireManage하면 CategoryAccessDeniedException이다 (R6)")
    void requireManageRejectsOtherCategory() {
        AdminScope scope = AdminScope.categories(Set.of(1L));

        assertThatThrownBy(() -> scope.requireManage(SPRING))
                .isInstanceOf(CategoryAccessDeniedException.class)
                .hasMessage("담당하지 않는 카테고리입니다.");
    }

    @Test
    @DisplayName("담당 카테고리가 없는 범위는 어떤 카테고리도 관리하지 않는다")
    void emptyScopeManagesNothing() {
        AdminScope scope = AdminScope.categories(Set.of());

        assertThat(scope.isOwner()).isFalse();
        assertThat(scope.canManage(JAVA)).isFalse();
        assertThatThrownBy(() -> scope.requireManage(JAVA)).isInstanceOf(CategoryAccessDeniedException.class);
    }

    @Test
    @DisplayName("만든 뒤 넘긴 집합을 바꿔도 범위는 바뀌지 않는다 (불변 값 객체)")
    void scopeIsImmutable() {
        Set<Long> ids = new HashSet<>(Set.of(1L));
        AdminScope scope = AdminScope.categories(ids);

        ids.add(2L);

        assertThat(scope.canManage(SPRING)).isFalse();
    }
}
