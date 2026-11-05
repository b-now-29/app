package com.b.now._29.app.quiz.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    private static final String SLUG = "java";
    private static final String QUESTION_CONTENT = "문자열 내용을 비교할 때 쓰는 것은?";
    private static final String EXPLANATION = "equals()는 문자열 내용을 비교한다.";
    private static final long QUESTION_ID = 1L;
    private static final long WRONG_CHOICE_ID = 10L;
    private static final long CORRECT_CHOICE_ID = 11L;
    private static final long SHORT_ANSWER_QUESTION_ID = 2L;
    private static final String SHORT_ANSWER_CONTENT = "문자열 내용은 어떻게 비교하는가?";
    private static final long ESSAY_QUESTION_ID = 3L;
    private static final String ESSAY_CONTENT = "equals()와 ==를 비교해 서술하세요.";
    private static final List<QuestionType> SOLVABLE_TYPES =
            List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY);

    @Mock
    QuestionRepository questionRepository;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    QuestionService questionService;

    @Test
    @DisplayName("없는 slug면 CategoryNotFoundException을 던지고 문제를 조회하지 않는다")
    void getQuestionsOfUnknownCategory() {
        given(categoryRepository.existsBySlug(SLUG)).willReturn(false);

        assertThatThrownBy(() -> questionService.getQuestions(SLUG)).isInstanceOf(CategoryNotFoundException.class);

        then(questionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("있는 slug에 세 유형의 문제가 모두 없으면 빈 목록을 돌려준다")
    void getQuestionsReturnsEmptyListWhenNoSolvableQuestions() {
        given(categoryRepository.existsBySlug(SLUG)).willReturn(true);
        given(questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(SLUG, SOLVABLE_TYPES))
                .willReturn(List.of());

        assertThat(questionService.getQuestions(SLUG)).isEmpty();
    }

    @Test
    @DisplayName("있는 slug면 리포지토리에 객관식, 주관식, 서술형을 넘기고 결과를 유형이 담긴 응답 DTO로 바꾼다 (R1, R2)")
    void getQuestionsRequestsAllSolvableTypes() {
        given(categoryRepository.existsBySlug(SLUG)).willReturn(true);
        given(questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(SLUG, SOLVABLE_TYPES))
                .willReturn(List.of(question(), shortAnswerQuestion(), essayQuestion()));

        List<QuestionResponse> responses = questionService.getQuestions(SLUG);

        then(questionRepository).should().findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(SLUG, SOLVABLE_TYPES);
        assertThat(responses)
                .containsExactly(
                        new QuestionResponse(
                                QUESTION_ID,
                                QuestionType.MULTIPLE_CHOICE,
                                QUESTION_CONTENT,
                                List.of(
                                        new ChoiceResponse(WRONG_CHOICE_ID, "=="),
                                        new ChoiceResponse(CORRECT_CHOICE_ID, "equals()"))),
                        new QuestionResponse(
                                SHORT_ANSWER_QUESTION_ID, QuestionType.SHORT_ANSWER, SHORT_ANSWER_CONTENT, List.of()),
                        new QuestionResponse(ESSAY_QUESTION_ID, QuestionType.ESSAY, ESSAY_CONTENT, List.of()));
    }

    @Test
    @DisplayName("없는 문제에 답을 제출하면 QuestionNotFoundException을 던진다")
    void submitAnswerToUnknownQuestion() {
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.submitAnswer(QUESTION_ID, CORRECT_CHOICE_ID))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("답을 제출하면 채점 결과를 응답 DTO로 바꾼다")
    void submitAnswerReturnsGradingResult() {
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question()));

        AnswerResponse response = questionService.submitAnswer(QUESTION_ID, WRONG_CHOICE_ID);

        assertThat(response).isEqualTo(new AnswerResponse(false, CORRECT_CHOICE_ID, EXPLANATION));
    }

    @Test
    @DisplayName("없는 문제의 모범 답안을 요청하면 QuestionNotFoundException을 던진다 (R8)")
    void submitTextAnswerToUnknownQuestion() {
        given(questionRepository.findById(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.submitTextAnswer(QUESTION_ID))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    /** ID는 DB가 만들지만 이 테스트는 DB 없이 돌기 때문에 필드에 직접 채운다. */
    private static Question shortAnswerQuestion() {
        Category category = new Category(SLUG, "Java", 10);
        Question question = new Question(category, QuestionType.SHORT_ANSWER, SHORT_ANSWER_CONTENT, EXPLANATION, 20);
        ReflectionTestUtils.setField(question, "id", SHORT_ANSWER_QUESTION_ID);
        return question;
    }

    /** ID는 DB가 만들지만 이 테스트는 DB 없이 돌기 때문에 필드에 직접 채운다. */
    private static Question essayQuestion() {
        Category category = new Category(SLUG, "Java", 10);
        Question question = new Question(category, QuestionType.ESSAY, ESSAY_CONTENT, EXPLANATION, 30);
        ReflectionTestUtils.setField(question, "id", ESSAY_QUESTION_ID);
        return question;
    }

    /** ID는 DB가 만들지만 이 테스트는 DB 없이 돌기 때문에 필드에 직접 채운다. */
    private static Question question() {
        Category category = new Category(SLUG, "Java", 10);
        Question question = new Question(category, QuestionType.MULTIPLE_CHOICE, QUESTION_CONTENT, EXPLANATION, 10);
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        ReflectionTestUtils.setField(question.addChoice("==", false, 10), "id", WRONG_CHOICE_ID);
        ReflectionTestUtils.setField(question.addChoice("equals()", true, 20), "id", CORRECT_CHOICE_ID);
        return question;
    }
}
