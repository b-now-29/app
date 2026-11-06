package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotEmptyException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.category.DuplicateCategorySlugException;
import com.b.now._29.app.quiz.question.QuestionRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 구현할 API ({@code quiz.admin}, D15: admin → category, question 한쪽 의존):
 *
 * <ul>
 *   <li>{@code @Service AdminCategoryService(CategoryRepository, QuestionRepository, AdminCategoryRepository)}
 *   <li>{@code List<AdminCategoryResponse> getCategories()} — {@code AdminCategoryRepository.findAdminCategories()} 결과
 *   <li>{@code AdminCategoryResponse create(CategoryCreateRequest request)} — 삭제한 것을 포함해 slug가 있으면 {@code
 *       DuplicateCategorySlugException(String slug)} ({@code quiz.category}). 저장은 {@code saveAndFlush}, 유니크 제약
 *       위반도 같은 예외로 바꾼다
 *   <li>{@code AdminCategoryResponse update(String slug, CategoryUpdateRequest request)}
 *   <li>{@code void delete(String slug)} — {@code countByCategoryAndDeletedAtIsNull} 결과를 {@code category.delete(count)}에 넘긴다
 *   <li>DTO ({@code quiz.admin}): {@code record CategoryCreateRequest(String slug, String name, String description, int
 *       sortOrder)}, {@code record CategoryUpdateRequest(String name, String description, int sortOrder)}, {@code record
 *       AdminCategoryResponse(String slug, String name, String description, int sortOrder, long questionCount)}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    private static final String SLUG = "kotlin";

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    AdminCategoryRepository adminCategoryRepository;

    @InjectMocks
    AdminCategoryService adminCategoryService;

    @Test
    @DisplayName("새 slug면 카테고리를 저장하고 문제 수 0으로 응답한다 (R4)")
    void createsCategory() {
        given(categoryRepository.existsBySlug(SLUG)).willReturn(false);
        given(categoryRepository.saveAndFlush(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        AdminCategoryResponse response =
                adminCategoryService.create(new CategoryCreateRequest(SLUG, "Kotlin", "코틀린 기초", 40));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        then(categoryRepository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSlug()).isEqualTo(SLUG);
        assertThat(saved.getValue().getDescription()).isEqualTo("코틀린 기초");
        assertThat(response).isEqualTo(new AdminCategoryResponse(SLUG, "Kotlin", "코틀린 기초", 40, 0));
    }

    @Test
    @DisplayName("이미 있는 slug면 (삭제한 카테고리 포함) DuplicateCategorySlugException이고 저장하지 않는다 (R4, D7)")
    void rejectsDuplicateSlug() {
        given(categoryRepository.existsBySlug(SLUG)).willReturn(true);

        assertThatThrownBy(() -> adminCategoryService.create(new CategoryCreateRequest(SLUG, "Kotlin", null, 40)))
                .isInstanceOf(DuplicateCategorySlugException.class);

        then(categoryRepository).should(never()).saveAndFlush(any(Category.class));
    }

    @Test
    @DisplayName("동시 등록으로 유니크 제약을 어겨도 500이 아니라 DuplicateCategorySlugException이다 (R4)")
    void convertsUniqueViolation() {
        given(categoryRepository.existsBySlug(SLUG)).willReturn(false);
        given(categoryRepository.saveAndFlush(any(Category.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> adminCategoryService.create(new CategoryCreateRequest(SLUG, "Kotlin", null, 40)))
                .isInstanceOf(DuplicateCategorySlugException.class);
    }

    @Test
    @DisplayName("수정하면 이름, 설명, 표시 순서를 바꾸고 남은 문제 수와 함께 응답한다 (R4)")
    void updatesCategory() {
        Category category = new Category(SLUG, "Kotlin", null, 40);
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(3L);

        AdminCategoryResponse response = adminCategoryService.update(SLUG, new CategoryUpdateRequest("코틀린", "설명", 5));

        assertThat(category.getName()).isEqualTo("코틀린");
        assertThat(response).isEqualTo(new AdminCategoryResponse(SLUG, "코틀린", "설명", 5, 3));
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리를 수정하면 CategoryNotFoundException이다")
    void updateOfUnknownCategory() {
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminCategoryService.update(SLUG, new CategoryUpdateRequest("코틀린", null, 5)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("남은 문제가 없으면 soft delete 한다 (R6, D2)")
    void deletesCategory() {
        Category category = new Category(SLUG, "Kotlin", null, 40);
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(0L);

        adminCategoryService.delete(SLUG);

        assertThat(category.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제 안 된 문제가 남아 있으면 CategoryNotEmptyException이고 삭제하지 않는다 (R7)")
    void rejectsDeleteOfCategoryWithQuestions() {
        Category category = new Category(SLUG, "Kotlin", null, 40);
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(2L);

        assertThatThrownBy(() -> adminCategoryService.delete(SLUG)).isInstanceOf(CategoryNotEmptyException.class);

        assertThat(category.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("없거나 이미 삭제한 카테고리를 삭제하면 CategoryNotFoundException이다")
    void deleteOfUnknownCategory() {
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminCategoryService.delete(SLUG)).isInstanceOf(CategoryNotFoundException.class);
    }
}
