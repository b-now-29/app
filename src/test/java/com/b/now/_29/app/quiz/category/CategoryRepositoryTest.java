package com.b.now._29.app.quiz.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 구현할 Repository 메서드 (R4, R6, R7, D7):
 *
 * <ul>
 *   <li>{@code List<Category> findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc()} — 공개 카테고리 목록 ({@code CategoryService})
 *   <li>{@code boolean existsBySlug(String slug)} — 삭제한 카테고리도 센다 (slug 중복 검사, D7)
 *   <li>{@code boolean existsBySlugAndDeletedAtIsNull(String slug)} — 공개 문제 목록의 카테고리 확인
 *   <li>{@code Optional<Category> findBySlugAndDeletedAtIsNull(String slug)} — 관리자 수정·삭제·문제 등록
 * </ul>
 *
 * <p>문제 수를 함께 세는 관리자 목록은 {@code quiz.admin.AdminCategoryRepository}에 있다 (D15: category는 question을 참조하지 않는다).
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class CategoryRepositoryTest {

    @Autowired
    CategoryRepository categoryRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("시드 카테고리를 표시 순서대로 조회한다")
    void findsSeededCategoriesInSortOrder() {
        assertThat(categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc())
                .extracting(Category::getSlug)
                .containsSubsequence("java", "spring", "database");
    }

    @Test
    @DisplayName("저장한 순서가 아니라 표시 순서로 정렬한다")
    void sortsBySortOrderNotInsertionOrder() {
        categoryRepository.saveAndFlush(new Category("saved-first", "먼저 저장", 1001));
        categoryRepository.saveAndFlush(new Category("saved-second", "나중에 저장", 1000));

        assertThat(categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc())
                .extracting(Category::getSlug)
                .containsSubsequence("saved-second", "saved-first");
    }

    @Test
    @DisplayName("공개 카테고리 목록은 삭제한 카테고리를 뺀다 (R6)")
    void publicListExcludesDeletedCategory() {
        categoryRepository.saveAndFlush(new Category("kept", "남은 카테고리", 1000));
        saveDeleted("removed");
        entityManager.clear();

        assertThat(categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc())
                .extracting(Category::getSlug)
                .contains("kept")
                .doesNotContain("removed");
    }

    @Test
    @DisplayName("slug가 중복되면 저장할 수 없다")
    void rejectsDuplicateSlug() {
        categoryRepository.saveAndFlush(new Category("duplicate", "첫 번째", 1000));
        Category duplicate = new Category("duplicate", "두 번째", 1001);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("삭제한 카테고리의 slug로도 저장할 수 없다 (D7)")
    void rejectsSlugOfDeletedCategory() {
        saveDeleted("reused");
        Category duplicate = new Category("reused", "다시 만든 카테고리", 1001);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장된 slug면 existsBySlug는 참이다")
    void existsBySlugIsTrueForSavedSlug() {
        categoryRepository.saveAndFlush(new Category("saved-slug", "저장한 카테고리", 1000));

        assertThat(categoryRepository.existsBySlug("saved-slug")).isTrue();
    }

    @Test
    @DisplayName("없는 slug면 existsBySlug는 거짓이다")
    void existsBySlugIsFalseForUnknownSlug() {
        assertThat(categoryRepository.existsBySlug("unknown-slug")).isFalse();
    }

    @Test
    @DisplayName("삭제한 카테고리의 slug도 existsBySlug는 참이다 (D7)")
    void existsBySlugIncludesDeleted() {
        saveDeleted("deleted-slug");
        entityManager.clear();

        assertThat(categoryRepository.existsBySlug("deleted-slug")).isTrue();
    }

    @Test
    @DisplayName("existsBySlugAndDeletedAtIsNull은 남은 카테고리만 참이다 (R6)")
    void existsActiveSlug() {
        categoryRepository.saveAndFlush(new Category("active-slug", "남은 카테고리", 1000));
        saveDeleted("deleted-slug");
        entityManager.clear();

        assertThat(categoryRepository.existsBySlugAndDeletedAtIsNull("active-slug"))
                .isTrue();
        assertThat(categoryRepository.existsBySlugAndDeletedAtIsNull("deleted-slug"))
                .isFalse();
        assertThat(categoryRepository.existsBySlugAndDeletedAtIsNull("unknown-slug"))
                .isFalse();
    }

    @Test
    @DisplayName("findBySlugAndDeletedAtIsNull은 삭제한 카테고리를 찾지 않는다 (R6)")
    void findsOnlyActiveCategoryBySlug() {
        categoryRepository.saveAndFlush(new Category("active-slug", "남은 카테고리", "설명", 1000));
        saveDeleted("deleted-slug");
        entityManager.clear();

        assertThat(categoryRepository.findBySlugAndDeletedAtIsNull("active-slug"))
                .hasValueSatisfying(found -> assertThat(found.getName()).isEqualTo("남은 카테고리"));
        assertThat(categoryRepository.findBySlugAndDeletedAtIsNull("deleted-slug"))
                .isEmpty();
    }

    @Test
    @DisplayName("description과 deleted_at 컬럼을 매핑한다 (D2)")
    void mapsDescriptionAndDeletedAt() {
        Category category = categoryRepository.saveAndFlush(new Category("mapped", "매핑", "설명 문장", 1000));
        category.delete(0);
        categoryRepository.saveAndFlush(category);
        entityManager.clear();

        Category found = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(found.getDescription()).isEqualTo("설명 문장");
        assertThat(found.getSortOrder()).isEqualTo(1000);
        assertThat(found.isDeleted()).isTrue();
        assertThat(entityManager
                        .createNativeQuery("SELECT deleted_at FROM category WHERE slug = 'mapped'")
                        .getSingleResult())
                .isNotNull();
    }

    private void saveDeleted(String slug) {
        Category category = categoryRepository.saveAndFlush(new Category(slug, "삭제한 카테고리", 1000));
        category.delete(0);
        categoryRepository.saveAndFlush(category);
    }
}
