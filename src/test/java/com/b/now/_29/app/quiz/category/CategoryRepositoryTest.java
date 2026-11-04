package com.b.now._29.app.quiz.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class CategoryRepositoryTest {

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    @DisplayName("시드 카테고리를 표시 순서대로 조회한다")
    void findsSeededCategoriesInSortOrder() {
        assertThat(categoryRepository.findAllByOrderBySortOrderAsc())
                .extracting(Category::getSlug)
                .containsSubsequence("java", "spring", "database");
    }

    @Test
    @DisplayName("저장한 순서가 아니라 표시 순서로 정렬한다")
    void sortsBySortOrderNotInsertionOrder() {
        categoryRepository.saveAndFlush(new Category("saved-first", "먼저 저장", 1001));
        categoryRepository.saveAndFlush(new Category("saved-second", "나중에 저장", 1000));

        assertThat(categoryRepository.findAllByOrderBySortOrderAsc())
                .extracting(Category::getSlug)
                .containsSubsequence("saved-second", "saved-first");
    }

    @Test
    @DisplayName("slug가 중복되면 저장할 수 없다")
    void rejectsDuplicateSlug() {
        categoryRepository.saveAndFlush(new Category("duplicate", "첫 번째", 1000));
        Category duplicate = new Category("duplicate", "두 번째", 1001);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
