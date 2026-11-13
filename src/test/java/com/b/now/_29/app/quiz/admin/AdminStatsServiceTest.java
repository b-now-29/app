package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 카테고리 목록과 (카테고리, 유형) 집계 행을 합치는 로직 (docs/features/admin-stats.md R2, R3). Repository는 mock. */
@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock
    AdminStatsRepository adminStatsRepository;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    AdminStatsService adminStatsService;

    @Test
    @DisplayName("카테고리는 Repository 순서를 지키고, types는 enum 순서로 정렬하며, 기록이 없는 카테고리는 빈 목록이다 (R2)")
    void mergesTypeRowsIntoCategories() {
        given(categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc())
                .willReturn(List.of(new Category("java", "Java", 1), new Category("spring", "Spring", 2)));
        // 쿼리 결과 순서는 보장되지 않으므로 enum 순서와 반대로 준다
        given(adminStatsRepository.findCategoryTypeStats())
                .willReturn(List.of(
                        new AdminCategoryTypeStat("java", QuestionType.ESSAY, 3, 1),
                        new AdminCategoryTypeStat("java", QuestionType.SHORT_ANSWER, 2, 2),
                        new AdminCategoryTypeStat("java", QuestionType.MULTIPLE_CHOICE, 12, 9)));

        List<AdminCategoryStatResponse> stats = adminStatsService.getCategoryStats();

        assertThat(stats)
                .containsExactly(
                        new AdminCategoryStatResponse(
                                "java",
                                "Java",
                                List.of(
                                        new AdminTypeStatResponse(QuestionType.MULTIPLE_CHOICE, 12, 9),
                                        new AdminTypeStatResponse(QuestionType.SHORT_ANSWER, 2, 2),
                                        new AdminTypeStatResponse(QuestionType.ESSAY, 3, 1))),
                        new AdminCategoryStatResponse("spring", "Spring", List.of()));
    }

    @Test
    @DisplayName("카테고리 목록에 없는 slug의 집계 행 (삭제된 카테고리 등)은 결과에 나오지 않는다 (R4)")
    void ignoresRowsOfUnknownCategories() {
        given(categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc())
                .willReturn(List.of(new Category("java", "Java", 1)));
        given(adminStatsRepository.findCategoryTypeStats())
                .willReturn(List.of(new AdminCategoryTypeStat("deleted", QuestionType.ESSAY, 1, 1)));

        assertThat(adminStatsService.getCategoryStats())
                .containsExactly(new AdminCategoryStatResponse("java", "Java", List.of()));
    }

    @Test
    @DisplayName("문제 통계는 불변 목록으로 반환한다 (backend.md: 컬렉션은 List.copyOf)")
    void questionStatsAreImmutable() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull("java")).willReturn(true);
        given(adminStatsRepository.findQuestionStats("java"))
                .willReturn(
                        new ArrayList<>(List.of(new AdminQuestionStatResponse(1L, QuestionType.ESSAY, "문제", 0, 0))));

        List<AdminQuestionStatResponse> stats = adminStatsService.getQuestionStats("java");

        assertThat(stats).hasSize(1);
        assertThatThrownBy(stats::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리면 CategoryNotFoundException이고 통계 쿼리를 실행하지 않는다 (404)")
    void unknownCategoryIsNotFound() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull("nope")).willReturn(false);

        assertThatThrownBy(() -> adminStatsService.getQuestionStats("nope"))
                .isInstanceOf(CategoryNotFoundException.class);
        then(adminStatsRepository).should(never()).findQuestionStats("nope");
    }
}
