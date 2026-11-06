package com.b.now._29.app.quiz.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 카테고리 수정·삭제 (R4, R6, R7).
 *
 * <p>구현할 API: {@code new Category(String slug, String name, String description, int sortOrder)}, {@code
 * getDescription()}, {@code getSortOrder()}, {@code void update(String name, String description, int sortOrder)},
 * {@code void delete(long remainingQuestionCount)} (남은 문제가 있으면 {@code CategoryNotEmptyException(String slug)}),
 * {@code boolean isDeleted()}.
 */
class CategoryTest {

    @Test
    @DisplayName("slug, 이름, 설명, 표시 순서로 카테고리를 만든다 (R4)")
    void createsCategory() {
        Category category = new Category("kotlin", "Kotlin", "코틀린 기초", 40);

        assertThat(category.getSlug()).isEqualTo("kotlin");
        assertThat(category.getName()).isEqualTo("Kotlin");
        assertThat(category.getDescription()).isEqualTo("코틀린 기초");
        assertThat(category.getSortOrder()).isEqualTo(40);
        assertThat(category.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("수정하면 이름, 설명, 표시 순서가 바뀌고 slug는 그대로다 (R4)")
    void updatesWithoutChangingSlug() {
        Category category = new Category("kotlin", "Kotlin", "코틀린 기초", 40);

        category.update("코틀린", null, 5);

        assertThat(category.getSlug()).isEqualTo("kotlin");
        assertThat(category.getName()).isEqualTo("코틀린");
        assertThat(category.getDescription()).isNull();
        assertThat(category.getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("삭제되지 않은 문제가 없으면 삭제 상태가 된다 (R6, D2)")
    void deletesWhenNoRemainingQuestions() {
        Category category = new Category("kotlin", "Kotlin", null, 40);

        category.delete(0);

        assertThat(category.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제되지 않은 문제가 남아 있으면 CategoryNotEmptyException이고 삭제되지 않는다 (R7)")
    void rejectsDeleteWithRemainingQuestions() {
        Category category = new Category("kotlin", "Kotlin", null, 40);

        assertThatThrownBy(() -> category.delete(1)).isInstanceOf(CategoryNotEmptyException.class);

        assertThat(category.isDeleted()).isFalse();
    }
}
