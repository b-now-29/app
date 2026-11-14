package com.b.now._29.app.quiz.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 구현할 API.
 *
 * <ul>
 *   <li>{@code WrongAnswerService(WrongAnswerRepository, CategoryRepository)}
 *   <li>{@code List<WrongAnswerCategoryResponse> getWrongAnswers(long userId)}
 *   <li>{@code List<QuestionResponse> getWrongQuestions(long userId, String slug)}: 없는·삭제된 카테고리는
 *       {@code CategoryNotFoundException}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WrongAnswerServiceTest {

    private static final long USER_ID = 7L;
    private static final Instant BASE = Instant.parse("2026-11-06T01:00:00Z");

    @Mock
    WrongAnswerRepository wrongAnswerRepository;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    WrongAnswerService wrongAnswerService;

    // 오답 목록

    @Test
    @DisplayName("오답이 없으면 빈 목록이다")
    void wrongAnswersAreEmptyWithoutRows() {
        given(wrongAnswerRepository.findWrongAnswers(USER_ID)).willReturn(List.of());

        assertThat(wrongAnswerService.getWrongAnswers(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("평평한 행을 카테고리별로 묶고, 카테고리와 문제는 행이 온 순서를 유지한다 (R6)")
    void nestsRowsUnderCategoryKeepingOrder() {
        given(wrongAnswerRepository.findWrongAnswers(USER_ID))
                .willReturn(List.of(
                        new WrongAnswerRow("spring", "스프링", 5L, "스프링 문제", QuestionType.MULTIPLE_CHOICE, BASE),
                        new WrongAnswerRow("java", "자바", 3L, "자바 서술형", QuestionType.ESSAY, BASE.plusSeconds(60)),
                        new WrongAnswerRow("java", "자바", 1L, "자바 객관식", QuestionType.MULTIPLE_CHOICE, BASE)));

        assertThat(wrongAnswerService.getWrongAnswers(USER_ID))
                .containsExactly(
                        new WrongAnswerCategoryResponse(
                                "spring",
                                "스프링",
                                List.of(new WrongAnswerQuestionResponse(
                                        5L, "스프링 문제", QuestionType.MULTIPLE_CHOICE, BASE))),
                        new WrongAnswerCategoryResponse(
                                "java",
                                "자바",
                                List.of(
                                        new WrongAnswerQuestionResponse(
                                                3L, "자바 서술형", QuestionType.ESSAY, BASE.plusSeconds(60)),
                                        new WrongAnswerQuestionResponse(
                                                1L, "자바 객관식", QuestionType.MULTIPLE_CHOICE, BASE))));
    }

    // 다시 풀기용 문제

    @Test
    @DisplayName("없거나 삭제된 카테고리는 CategoryNotFoundException이고 문제를 조회하지 않는다")
    void wrongQuestionsOfUnknownCategory() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull("nope")).willReturn(false);

        assertThatThrownBy(() -> wrongAnswerService.getWrongQuestions(USER_ID, "nope"))
                .isInstanceOf(CategoryNotFoundException.class);
        then(wrongAnswerRepository).should(never()).findWrongQuestions(USER_ID, "nope");
    }

    @Test
    @DisplayName("오답이 없는 카테고리는 빈 목록이다")
    void wrongQuestionsAreEmptyWithoutWrongAnswers() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull("java")).willReturn(true);
        given(wrongAnswerRepository.findWrongQuestions(USER_ID, "java")).willReturn(List.of());

        assertThat(wrongAnswerService.getWrongQuestions(USER_ID, "java")).isEmpty();
    }
}
