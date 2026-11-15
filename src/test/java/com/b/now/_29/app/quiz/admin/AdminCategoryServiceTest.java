package com.b.now._29.app.quiz.admin;

import static com.b.now._29.app.support.TestCategories.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryHasSoleAdminException;
import com.b.now._29.app.quiz.category.CategoryNotEmptyException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.category.DuplicateCategorySlugException;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 구현할 API ({@code quiz.admin}, D15: admin → category, question 한쪽 의존):
 *
 * <ul>
 *   <li>{@code @Service AdminCategoryService(CategoryRepository, QuestionRepository, AdminCategoryRepository,
 *       AdminScopeService, CategoryAdminRepository, UserRepository)} (role-owner-category-admin, D13)
 *   <li>{@code List<AdminCategoryResponse> getCategories(long actorId)} — OWNER면 {@code
 *       AdminCategoryRepository.findAdminCategories()} 결과, CATEGORY_ADMIN이면 담당 카테고리만
 *   <li>{@code update(long actorId, String slug, CategoryUpdateRequest)} — 담당이 아니면 {@code
 *       CategoryAccessDeniedException}
 *   <li>{@code delete(String slug)} — {@code categoryAdminRepository.countAdminsWithOnlyCategory(categoryId)}를 {@code
 *       category.delete(questionCount, soleAdminCount)}에 넘긴다
 *   <li>{@code AdminCategoryResponse create(CategoryCreateRequest request)} — 삭제한 것을 포함해 slug가 있으면 {@code
 *       DuplicateCategorySlugException(String slug)} ({@code quiz.category}). 저장은 {@code saveAndFlush}, 유니크 제약
 *       위반도 같은 예외로 바꾼다
 *   <li>{@code AdminCategoryResponse update(String slug, CategoryUpdateRequest request)}
 *   <li>{@code void delete(String slug)} — 카테고리는 {@code findWithWriteLockBySlugAndDeletedAtIsNull}, 그다음 담당 관리자
 *       ({@code CategoryAdminRepository.findUserIdsByCategoryId})의 users 행을 {@code
 *       UserRepository.findWithWriteLockByIdInOrderByIdAsc}로 잠근 뒤 유일 담당 수를 센다 (D13)
 *   <li>DTO ({@code quiz.admin}): {@code record CategoryCreateRequest(String slug, String name, String description, int
 *       sortOrder)}, {@code record CategoryUpdateRequest(String name, String description, int sortOrder)}, {@code record
 *       AdminCategoryResponse(String slug, String name, String description, int sortOrder, long questionCount)}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    private static final String SLUG = "kotlin";
    private static final long ACTOR_ID = 1L;
    private static final long CATEGORY_ID = 40L;
    private static final long OTHER_CATEGORY_ID = 41L;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    AdminCategoryRepository adminCategoryRepository;

    @Mock
    AdminScopeService adminScopeService;

    @Mock
    CategoryAdminRepository categoryAdminRepository;

    @Mock
    UserRepository userRepository;

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

    // 목록 (role-owner-category-admin R4). CATEGORY_ADMIN의 담당만 보기는 AdminContentApiIntegrationTest에서 확인한다

    @Test
    @DisplayName("OWNER의 카테고리 목록은 관리자 카테고리 조회 결과 전체다 (R4)")
    void ownerGetsAllCategories() {
        List<AdminCategoryResponse> all = List.of(
                new AdminCategoryResponse("java", "Java", null, 10, 3),
                new AdminCategoryResponse(SLUG, "Kotlin", null, 40, 0));
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.owner());
        given(adminCategoryRepository.findAdminCategories()).willReturn(all);

        assertThat(adminCategoryService.getCategories(ACTOR_ID)).isEqualTo(all);
    }

    // 수정

    @Test
    @DisplayName("수정하면 이름, 설명, 표시 순서를 바꾸고 남은 문제 수와 함께 응답한다 (R4)")
    void updatesCategory() {
        Category category = withId(CATEGORY_ID, SLUG);
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.owner());
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(3L);

        AdminCategoryResponse response =
                adminCategoryService.update(ACTOR_ID, SLUG, new CategoryUpdateRequest("코틀린", "설명", 5));

        assertThat(category.getName()).isEqualTo("코틀린");
        assertThat(response).isEqualTo(new AdminCategoryResponse(SLUG, "코틀린", "설명", 5, 3));
    }

    @Test
    @DisplayName("CATEGORY_ADMIN은 담당 카테고리를 수정할 수 있다 (R4)")
    void categoryAdminUpdatesAssignedCategory() {
        Category category = withId(CATEGORY_ID, SLUG);
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(CATEGORY_ID)));
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(0L);

        AdminCategoryResponse response =
                adminCategoryService.update(ACTOR_ID, SLUG, new CategoryUpdateRequest("코틀린", null, 5));

        assertThat(response.name()).isEqualTo("코틀린");
    }

    @Test
    @DisplayName("CATEGORY_ADMIN이 담당이 아닌 카테고리를 수정하면 CategoryAccessDeniedException이고 바꾸지 않는다 (R4, R6)")
    void categoryAdminCannotUpdateOtherCategory() {
        Category category = withId(CATEGORY_ID, SLUG);
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(OTHER_CATEGORY_ID)));
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> adminCategoryService.update(ACTOR_ID, SLUG, new CategoryUpdateRequest("바꿈", "설명", 5)))
                .isInstanceOf(CategoryAccessDeniedException.class);

        assertThat(category.getName()).isEqualTo(SLUG);
        assertThat(category.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리를 수정하면 CategoryNotFoundException이다")
    void updateOfUnknownCategory() {
        lenient().when(adminScopeService.of(ACTOR_ID)).thenReturn(AdminScope.owner());
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminCategoryService.update(ACTOR_ID, SLUG, new CategoryUpdateRequest("코틀린", null, 5)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    // 삭제 (OWNER만, 경로 인가는 SecurityConfig)

    @Test
    @DisplayName("남은 문제와 유일한 담당 관리자가 없으면 soft delete 한다 (R6, D2)")
    void deletesCategory() {
        Category category = withId(CATEGORY_ID, SLUG);
        given(categoryRepository.findWithWriteLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(0L);
        given(categoryAdminRepository.countAdminsWithOnlyCategory(CATEGORY_ID)).willReturn(0L);

        adminCategoryService.delete(SLUG);

        assertThat(category.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제는 카테고리 행 → 담당 관리자 행 (id 순) 잠금 → 유일 담당 수 세기 순서로 한다 (R10, D13)")
    void deleteLocksCategoryThenAdminsBeforeCounting() {
        Category category = withId(CATEGORY_ID, SLUG);
        List<Long> adminIds = List.of(7L, 3L);
        given(categoryRepository.findWithWriteLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(0L);
        given(categoryAdminRepository.findUserIdsByCategoryId(CATEGORY_ID)).willReturn(adminIds);
        given(categoryAdminRepository.countAdminsWithOnlyCategory(CATEGORY_ID)).willReturn(0L);

        adminCategoryService.delete(SLUG);

        InOrder order = inOrder(categoryRepository, categoryAdminRepository, userRepository);
        order.verify(categoryRepository).findWithWriteLockBySlugAndDeletedAtIsNull(SLUG);
        order.verify(categoryAdminRepository).findUserIdsByCategoryId(CATEGORY_ID);
        order.verify(userRepository).findWithWriteLockByIdInOrderByIdAsc(adminIds);
        order.verify(categoryAdminRepository).countAdminsWithOnlyCategory(CATEGORY_ID);
        assertThat(category.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제 안 된 문제가 남아 있으면 CategoryNotEmptyException이고 삭제하지 않는다 (R7)")
    void rejectsDeleteOfCategoryWithQuestions() {
        Category category = withId(CATEGORY_ID, SLUG);
        given(categoryRepository.findWithWriteLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(2L);
        lenient()
                .when(categoryAdminRepository.countAdminsWithOnlyCategory(CATEGORY_ID))
                .thenReturn(0L);

        assertThatThrownBy(() -> adminCategoryService.delete(SLUG)).isInstanceOf(CategoryNotEmptyException.class);

        assertThat(category.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("이 카테고리만 담당하는 관리자가 있으면 CategoryHasSoleAdminException이고 삭제하지 않는다 (R10, D10)")
    void rejectsDeleteOfSoleAdminCategory() {
        Category category = withId(CATEGORY_ID, SLUG);
        given(categoryRepository.findWithWriteLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(category));
        given(questionRepository.countByCategoryAndDeletedAtIsNull(category)).willReturn(0L);
        given(categoryAdminRepository.countAdminsWithOnlyCategory(CATEGORY_ID)).willReturn(1L);

        assertThatThrownBy(() -> adminCategoryService.delete(SLUG)).isInstanceOf(CategoryHasSoleAdminException.class);

        assertThat(category.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("없거나 이미 삭제한 카테고리를 삭제하면 CategoryNotFoundException이다")
    void deleteOfUnknownCategory() {
        given(categoryRepository.findWithWriteLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adminCategoryService.delete(SLUG)).isInstanceOf(CategoryNotFoundException.class);
    }
}
