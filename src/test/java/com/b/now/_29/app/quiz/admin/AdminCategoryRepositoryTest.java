package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.ChoiceDraft;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionDraft;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 관리자 카테고리 목록 조회 (API 계약, D15). 구현할 Repository: {@code quiz.admin.AdminCategoryRepository} (Spring Data
 * Repository, 예: {@code Repository<Category, Long>}) 의 {@code List<AdminCategoryResponse> findAdminCategories()} — 삭제 안 된
 * 카테고리를 표시 순서로, questionCount는 삭제 안 된 문제만 센다.
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class AdminCategoryRepositoryTest {

    @Autowired
    AdminCategoryRepository adminCategoryRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    QuestionRepository questionRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("삭제 안 된 카테고리를 표시 순서로 주고, questionCount는 삭제 안 된 문제만 센다 (API 계약)")
    void countsOnlyActiveQuestions() {
        Category later = categoryRepository.saveAndFlush(new Category("admin-later", "나중", "설명", 2001));
        Category earlier = categoryRepository.saveAndFlush(new Category("admin-earlier", "먼저", null, 2000));
        categoryRepository.saveAndFlush(new Category("admin-empty", "빈 카테고리", null, 2002));
        Category removed = categoryRepository.saveAndFlush(new Category("admin-deleted", "삭제한 카테고리", 1999));
        removed.delete(0);
        categoryRepository.saveAndFlush(removed);
        saveQuestion(later, "남은 문제 1");
        saveQuestion(later, "남은 문제 2");
        Question deleted = saveQuestion(later, "삭제한 문제");
        deleted.delete();
        questionRepository.saveAndFlush(deleted);
        saveQuestion(earlier, "먼저 카테고리의 문제");
        entityManager.clear();

        List<AdminCategoryResponse> mine = adminCategoryRepository.findAdminCategories().stream()
                .filter(category -> category.slug().startsWith("admin-"))
                .toList();

        assertThat(mine)
                .containsExactly(
                        new AdminCategoryResponse("admin-earlier", "먼저", null, 2000, 1),
                        new AdminCategoryResponse("admin-later", "나중", "설명", 2001, 2),
                        new AdminCategoryResponse("admin-empty", "빈 카테고리", null, 2002, 0));
    }

    @Test
    @DisplayName("시드 카테고리도 문제 수와 함께 들어 있다")
    void includesSeedCategories() {
        assertThat(adminCategoryRepository.findAdminCategories())
                .filteredOn(category -> "java".equals(category.slug()))
                .singleElement()
                .satisfies(java -> assertThat(java.questionCount()).isEqualTo(5));
    }

    private Question saveQuestion(Category category, String content) {
        return questionRepository.saveAndFlush(Question.create(
                category,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        content,
                        null,
                        null,
                        10,
                        List.of(new ChoiceDraft("정답", true), new ChoiceDraft("오답", false)))));
    }
}
