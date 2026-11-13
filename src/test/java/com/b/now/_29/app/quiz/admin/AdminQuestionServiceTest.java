package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.b.now._29.app.quiz.attempt.QuestionAttemptRepository;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.Choice;
import com.b.now._29.app.quiz.question.ChoiceDraft;
import com.b.now._29.app.quiz.question.InvalidQuestionException;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionDraft;
import com.b.now._29.app.quiz.question.QuestionNotFoundException;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.quiz.question.QuestionTypeChangeNotAllowedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 구현할 API ({@code quiz.admin}, D15):
 *
 * <ul>
 *   <li>{@code @Service AdminQuestionService(QuestionRepository, CategoryRepository, QuestionAttemptRepository)}
 *   <li>D17: {@code update}는 {@code questionAttemptRepository.existsByQuestionId(id)}로 풀이 기록 여부를 확인해 {@code
 *       question.requireTypeChangeAllowed(request.type(), attempted)}를 {@code clearChoices()}·{@code flush()}보다 먼저
 *       호출한다. 거부되면 보기를 비우지도 flush하지도 않는다
 *   <li>{@code List<AdminQuestionResponse> getQuestions(String categorySlug)} — 카테고리 확인은 {@code
 *       existsBySlugAndDeletedAtIsNull}, 목록은 {@code findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(slug, 세 유형)}
 *   <li>{@code AdminQuestionResponse getQuestion(long id)} — {@code findWithChoicesById}
 *   <li>{@code AdminQuestionResponse create(QuestionRequest request)} — 카테고리는 {@code findBySlugAndDeletedAtIsNull}, 저장은
 *       {@code saveAndFlush}
 *   <li>{@code AdminQuestionResponse update(long id, QuestionRequest request)} — {@code findWithChoicesById} 후 {@code
 *       question.update}
 *   <li>{@code void delete(long id)} — {@code findByIdAndDeletedAtIsNull} 후 {@code question.delete()}
 *   <li>DTO ({@code quiz.admin}): {@code record QuestionRequest(String categorySlug, QuestionType type, String content,
 *       String explanation, String modelAnswer, int sortOrder, List<ChoiceRequest> choices)}, {@code record
 *       ChoiceRequest(String content, boolean correct)}, {@code record AdminQuestionResponse(long id, String categorySlug,
 *       QuestionType type, String content, String explanation, String modelAnswer, int sortOrder,
 *       List<AdminChoiceResponse> choices)}, {@code record AdminChoiceResponse(long id, String content, boolean correct)}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionServiceTest {

    private static final String SLUG = "java";
    private static final long QUESTION_ID = 1001L;
    private static final List<QuestionType> ALL_TYPES =
            List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY);

    @Mock
    QuestionRepository questionRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    QuestionAttemptRepository questionAttemptRepository;

    @InjectMocks
    AdminQuestionService adminQuestionService;

    private Category java;

    @BeforeEach
    void setUp() {
        java = new Category(SLUG, "Java", null, 10);
    }

    @Test
    @DisplayName("문제 목록은 세 유형을 모두 조회하고 정답과 모범 답안을 포함해 응답한다 (R8)")
    void getQuestionsIncludesAnswers() {
        Question multipleChoice = withIds(Question.create(
                java,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        "객관식",
                        "해설",
                        null,
                        10,
                        List.of(new ChoiceDraft("A", false), new ChoiceDraft("B", true)))));
        Question shortAnswer = withIds(Question.create(
                java, new QuestionDraft(QuestionType.SHORT_ANSWER, "주관식", null, "모범 답안", 20, List.of())));
        ReflectionTestUtils.setField(shortAnswer, "id", 1002L);
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(SLUG, ALL_TYPES))
                .willReturn(List.of(multipleChoice, shortAnswer));

        List<AdminQuestionResponse> responses = adminQuestionService.getQuestions(SLUG);

        assertThat(responses)
                .containsExactly(
                        new AdminQuestionResponse(
                                QUESTION_ID,
                                SLUG,
                                QuestionType.MULTIPLE_CHOICE,
                                "객관식",
                                "해설",
                                null,
                                10,
                                List.of(
                                        new AdminChoiceResponse(1L, "A", false),
                                        new AdminChoiceResponse(2L, "B", true))),
                        new AdminQuestionResponse(
                                1002L, SLUG, QuestionType.SHORT_ANSWER, "주관식", null, "모범 답안", 20, List.of()));
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리의 문제 목록은 CategoryNotFoundException이고 문제를 조회하지 않는다")
    void getQuestionsOfUnknownCategory() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(false);

        assertThatThrownBy(() -> adminQuestionService.getQuestions(SLUG)).isInstanceOf(CategoryNotFoundException.class);

        then(questionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없거나 삭제한 문제를 단건 조회하면 QuestionNotFoundException이다 (R6)")
    void getUnknownQuestion() {
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.getQuestion(QUESTION_ID))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("문제를 등록하면 카테고리에 저장하고 id와 정답을 포함해 응답한다 (R5, R8)")
    void createsQuestion() {
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.create(new QuestionRequest(
                SLUG,
                QuestionType.MULTIPLE_CHOICE,
                "문제",
                null,
                null,
                0,
                List.of(new ChoiceRequest("A", true), new ChoiceRequest("B", false))));

        assertThat(response)
                .isEqualTo(new AdminQuestionResponse(
                        QUESTION_ID,
                        SLUG,
                        QuestionType.MULTIPLE_CHOICE,
                        "문제",
                        null,
                        null,
                        0,
                        List.of(new AdminChoiceResponse(1L, "A", true), new AdminChoiceResponse(2L, "B", false))));
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리에 문제를 등록하면 CategoryNotFoundException이고 저장하지 않는다")
    void createInUnknownCategory() {
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.create(validRequest()))
                .isInstanceOf(CategoryNotFoundException.class);

        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
        then(questionRepository).should(never()).save(any(Question.class));
    }

    @Test
    @DisplayName("유형별 규칙을 어긴 등록은 InvalidQuestionException이고 저장하지 않는다 (R5)")
    void rejectsInvalidQuestion() {
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.create(
                        new QuestionRequest(SLUG, QuestionType.ESSAY, "서술형", null, "  ", 0, List.of())))
                .isInstanceOf(InvalidQuestionException.class);

        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
        then(questionRepository).should(never()).save(any(Question.class));
    }

    @Test
    @DisplayName("없거나 삭제한 문제를 수정하면 QuestionNotFoundException이다")
    void updateOfUnknownQuestion() {
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.empty());
        lenient().when(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).thenReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.update(QUESTION_ID, validRequest()))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리로 옮기는 수정은 CategoryNotFoundException이고 문제를 바꾸지 않는다")
    void updateToUnknownCategory() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        lenient().when(questionRepository.findWithChoicesById(QUESTION_ID)).thenReturn(Optional.of(question));
        given(categoryRepository.findBySlugAndDeletedAtIsNull("removed")).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.update(
                        QUESTION_ID,
                        new QuestionRequest(
                                "removed", QuestionType.SHORT_ANSWER, "바뀐 문제", null, "모범 답안", 0, List.of())))
                .isInstanceOf(CategoryNotFoundException.class);

        assertThat(question.getContent()).isEqualTo("문제");
        assertThat(question.getCategory()).isSameAs(java);
    }

    @Test
    @DisplayName("풀이 기록이 있는 문제의 유형을 바꾸면 QuestionTypeChangeNotAllowedException이고 보기를 비우거나 flush하지 않는다 (D17)")
    void rejectsTypeChangeOfAttemptedQuestion() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        lenient().when(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).thenReturn(Optional.of(java));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(true);

        assertThatThrownBy(() -> adminQuestionService.update(
                        QUESTION_ID,
                        new QuestionRequest(SLUG, QuestionType.ESSAY, "서술형으로", null, "모범 답안", 0, List.of())))
                .isInstanceOf(QuestionTypeChangeNotAllowedException.class);

        assertThat(question.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(question.getChoices()).hasSize(2);
        then(questionRepository).should(never()).flush();
        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
    }

    @Test
    @DisplayName("풀이 기록이 있어도 유형이 같으면 내용과 보기를 고친다 (D17)")
    void updatesAttemptedQuestionKeepingType() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(true);
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.update(
                QUESTION_ID,
                new QuestionRequest(
                        SLUG,
                        QuestionType.MULTIPLE_CHOICE,
                        "고친 문제",
                        null,
                        null,
                        0,
                        List.of(new ChoiceRequest("X", false), new ChoiceRequest("Y", true))));

        assertThat(response.content()).isEqualTo("고친 문제");
        assertThat(response.choices()).extracting(AdminChoiceResponse::content).containsExactly("X", "Y");
        then(questionAttemptRepository).should().existsByQuestionId(QUESTION_ID);
    }

    @Test
    @DisplayName("풀이 기록이 없으면 유형을 바꿀 수 있다 (D6, D17)")
    void changesTypeWithoutAttempts() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(false);
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.update(
                QUESTION_ID, new QuestionRequest(SLUG, QuestionType.ESSAY, "서술형", null, "모범 답안", 0, List.of()));

        assertThat(response.type()).isEqualTo(QuestionType.ESSAY);
        assertThat(response.choices()).isEmpty();
    }

    @Test
    @DisplayName("문제를 삭제하면 soft delete 한다 (R6, D2)")
    void deletesQuestion() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findByIdAndDeletedAtIsNull(QUESTION_ID)).willReturn(Optional.of(question));

        adminQuestionService.delete(QUESTION_ID);

        assertThat(question.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("없거나 이미 삭제한 문제를 삭제하면 QuestionNotFoundException이다")
    void deleteOfUnknownQuestion() {
        given(questionRepository.findByIdAndDeletedAtIsNull(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.delete(QUESTION_ID))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    private static QuestionRequest validRequest() {
        return new QuestionRequest(
                SLUG,
                QuestionType.MULTIPLE_CHOICE,
                "문제",
                null,
                null,
                0,
                List.of(new ChoiceRequest("A", true), new ChoiceRequest("B", false)));
    }

    private static QuestionDraft validRequestDraft() {
        return new QuestionDraft(
                QuestionType.MULTIPLE_CHOICE,
                "문제",
                null,
                null,
                0,
                List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false)));
    }

    /** DB 없이 돌기 때문에 문제 id는 QUESTION_ID, 보기 id는 1부터 순서대로 채운다. */
    private static Question withIds(Question question) {
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        long choiceId = 1;
        for (Choice choice : question.getChoices()) {
            ReflectionTestUtils.setField(choice, "id", choiceId);
            choiceId++;
        }
        return question;
    }
}
