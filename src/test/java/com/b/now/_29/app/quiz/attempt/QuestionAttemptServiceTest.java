package com.b.now._29.app.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.b.now._29.app.quiz.question.QuestionType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionAttemptServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    QuestionAttemptRepository questionAttemptRepository;

    @InjectMocks
    QuestionAttemptService questionAttemptService;

    @Test
    @DisplayName("기록이 없으면 빈 목록이다")
    void categoryStatsAreEmptyWithoutAttempts() {
        given(questionAttemptRepository.findCategoryTypeStats(USER_ID)).willReturn(List.of());

        assertThat(questionAttemptService.getCategoryStats(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("평평한 행을 카테고리별로 묶고 types에는 기록 있는 유형만 넣는다. 카테고리는 조회 순서를 유지한다 (R6, D11)")
    void nestsTypeStatsUnderCategory() {
        given(questionAttemptRepository.findCategoryTypeStats(USER_ID))
                .willReturn(List.of(
                        new CategoryTypeStat("java", "자바", 10, QuestionType.MULTIPLE_CHOICE, 6, 5),
                        new CategoryTypeStat("java", "자바", 10, QuestionType.ESSAY, 4, 2),
                        new CategoryTypeStat("spring", "스프링", 20, QuestionType.SHORT_ANSWER, 1, 1)));

        assertThat(questionAttemptService.getCategoryStats(USER_ID))
                .containsExactly(
                        new CategoryStatResponse(
                                "java",
                                "자바",
                                List.of(
                                        new TypeStatResponse(QuestionType.MULTIPLE_CHOICE, 6, 5),
                                        new TypeStatResponse(QuestionType.ESSAY, 4, 2))),
                        new CategoryStatResponse(
                                "spring", "스프링", List.of(new TypeStatResponse(QuestionType.SHORT_ANSWER, 1, 1))));
    }

    @Test
    @DisplayName("행의 유형 순서와 상관없이 types는 MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY 순서다")
    void sortsTypesInFixedOrder() {
        given(questionAttemptRepository.findCategoryTypeStats(USER_ID))
                .willReturn(List.of(
                        new CategoryTypeStat("java", "자바", 10, QuestionType.ESSAY, 3, 1),
                        new CategoryTypeStat("java", "자바", 10, QuestionType.MULTIPLE_CHOICE, 2, 2),
                        new CategoryTypeStat("java", "자바", 10, QuestionType.SHORT_ANSWER, 1, 0)));

        assertThat(questionAttemptService.getCategoryStats(USER_ID))
                .singleElement()
                .satisfies(category -> assertThat(category.types())
                        .extracting(TypeStatResponse::type)
                        .containsExactly(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY));
    }

    @Test
    @DisplayName("카테고리 순서는 행이 온 순서(sort_order)를 따르고 slug 사전순으로 바꾸지 않는다")
    void keepsCategoryOrderOfRows() {
        given(questionAttemptRepository.findCategoryTypeStats(USER_ID))
                .willReturn(List.of(
                        new CategoryTypeStat("spring", "스프링", 10, QuestionType.MULTIPLE_CHOICE, 1, 1),
                        new CategoryTypeStat("database", "데이터베이스", 20, QuestionType.MULTIPLE_CHOICE, 1, 0),
                        new CategoryTypeStat("database", "데이터베이스", 20, QuestionType.ESSAY, 1, 1),
                        new CategoryTypeStat("java", "자바", 30, QuestionType.ESSAY, 1, 0)));

        assertThat(questionAttemptService.getCategoryStats(USER_ID))
                .extracting(CategoryStatResponse::slug)
                .containsExactly("spring", "database", "java");
    }
}
