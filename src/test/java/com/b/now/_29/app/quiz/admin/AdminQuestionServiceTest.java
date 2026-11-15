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
import java.util.Set;
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
 *   <li>{@code @Service AdminQuestionService(QuestionRepository, CategoryRepository, QuestionAttemptRepository,
 *       AdminScopeService)}
 *   <li>D17: {@code update}는 {@code questionAttemptRepository.existsByQuestionId(id)}로 풀이 기록 여부를 확인해 {@code
 *       question.requireTypeChangeAllowed(request.type(), attempted)}를 {@code clearChoices()}·{@code flush()}보다 먼저
 *       호출한다. 거부되면 보기를 비우지도 flush하지도 않는다
 *   <li>{@code List<AdminQuestionResponse> getQuestions(long actorId, String categorySlug)} — 카테고리는 {@code
 *       findBySlugAndDeletedAtIsNull}, 목록은 {@code findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(slug, 세 유형)}
 *   <li>{@code AdminQuestionResponse getQuestion(long actorId, long id)} — {@code findWithChoicesById}
 *   <li>{@code AdminQuestionResponse create(long actorId, QuestionRequest request)} — 카테고리는 {@code
 *       findWithShareLockBySlugAndDeletedAtIsNull}, 저장은 {@code saveAndFlush}
 *   <li>{@code AdminQuestionResponse update(long actorId, long id, QuestionRequest request)} — {@code findWithChoicesById} 후
 *       {@code question.update}
 *   <li>{@code void delete(long actorId, long id)} — {@code findByIdAndDeletedAtIsNull} 후 {@code question.delete()}
 *   <li>담당 범위 (role-owner-category-admin R4~R6): {@code adminScopeService.of(actorId)}로 받은 범위 밖의 카테고리나 그 안의 문제는
 *       {@code CategoryAccessDeniedException}. 문제를 옮길 때는 원래 카테고리와 옮겨 갈 카테고리를 모두 담당해야 한다
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionServiceTest {

    private static final String SLUG = "java";
    private static final long ACTOR_ID = 1L;
    private static final long JAVA_ID = 10L;
    private static final long SPRING_ID = 20L;
    private static final long QUESTION_ID = 1001L;
    private static final List<QuestionType> ALL_TYPES =
            List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY);

    @Mock
    QuestionRepository questionRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    QuestionAttemptRepository questionAttemptRepository;

    @Mock
    AdminScopeService adminScopeService;

    @InjectMocks
    AdminQuestionService adminQuestionService;

    private Category java;
    private Category spring;

    @BeforeEach
    void setUp() {
        java = new Category(SLUG, "Java", null, 10);
        ReflectionTestUtils.setField(java, "id", JAVA_ID);
        spring = new Category("spring", "Spring", null, 20);
        ReflectionTestUtils.setField(spring, "id", SPRING_ID);
        // 기본은 OWNER. 담당 범위를 보는 테스트는 다시 정한다
        lenient().when(adminScopeService.of(ACTOR_ID)).thenReturn(AdminScope.owner());
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
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));
        given(questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(SLUG, ALL_TYPES))
                .willReturn(List.of(multipleChoice, shortAnswer));

        List<AdminQuestionResponse> responses = adminQuestionService.getQuestions(ACTOR_ID, SLUG);

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
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.getQuestions(ACTOR_ID, SLUG))
                .isInstanceOf(CategoryNotFoundException.class);

        then(questionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CATEGORY_ADMIN은 담당 카테고리의 문제 목록을 본다 (R4)")
    void categoryAdminGetsQuestionsOfAssignedCategory() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(JAVA_ID)));
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));
        given(questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(SLUG, ALL_TYPES))
                .willReturn(List.of());

        assertThat(adminQuestionService.getQuestions(ACTOR_ID, SLUG)).isEmpty();
    }

    @Test
    @DisplayName("담당이 아닌 카테고리의 문제 목록은 CategoryAccessDeniedException이고 문제를 조회하지 않는다 (R6)")
    void getQuestionsOfOtherCategoryIsDenied() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(SPRING_ID)));
        given(categoryRepository.findBySlugAndDeletedAtIsNull(SLUG)).willReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.getQuestions(ACTOR_ID, SLUG))
                .isInstanceOf(CategoryAccessDeniedException.class);

        then(questionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없거나 삭제한 문제를 단건 조회하면 QuestionNotFoundException이다 (R6)")
    void getUnknownQuestion() {
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.getQuestion(ACTOR_ID, QUESTION_ID))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("담당 카테고리의 문제는 단건 조회할 수 있다 (R4)")
    void getQuestionOfAssignedCategory() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(JAVA_ID)));
        given(questionRepository.findWithChoicesById(QUESTION_ID))
                .willReturn(Optional.of(withIds(Question.create(java, validRequestDraft()))));

        assertThat(adminQuestionService.getQuestion(ACTOR_ID, QUESTION_ID).id()).isEqualTo(QUESTION_ID);
    }

    @Test
    @DisplayName("담당이 아닌 카테고리의 문제를 단건 조회하면 CategoryAccessDeniedException이다 (R6)")
    void getQuestionOfOtherCategoryIsDenied() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(SPRING_ID)));
        given(questionRepository.findWithChoicesById(QUESTION_ID))
                .willReturn(Optional.of(withIds(Question.create(java, validRequestDraft()))));

        assertThatThrownBy(() -> adminQuestionService.getQuestion(ACTOR_ID, QUESTION_ID))
                .isInstanceOf(CategoryAccessDeniedException.class);
    }

    @Test
    @DisplayName("문제를 등록하면 카테고리에 저장하고 id와 정답을 포함해 응답한다 (R5, R8)")
    void createsQuestion() {
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(java));
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.create(
                ACTOR_ID,
                new QuestionRequest(
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
    @DisplayName("담당이 아닌 카테고리에 문제를 등록하면 CategoryAccessDeniedException이고 저장하지 않는다 (R6)")
    void createInOtherCategoryIsDenied() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(SPRING_ID)));
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.create(ACTOR_ID, validRequest()))
                .isInstanceOf(CategoryAccessDeniedException.class);

        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
        then(questionRepository).should(never()).save(any(Question.class));
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리에 문제를 등록하면 CategoryNotFoundException이고 저장하지 않는다")
    void createInUnknownCategory() {
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.create(ACTOR_ID, validRequest()))
                .isInstanceOf(CategoryNotFoundException.class);

        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
        then(questionRepository).should(never()).save(any(Question.class));
    }

    @Test
    @DisplayName("유형별 규칙을 어긴 등록은 InvalidQuestionException이고 저장하지 않는다 (R5)")
    void rejectsInvalidQuestion() {
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.create(
                        ACTOR_ID, new QuestionRequest(SLUG, QuestionType.ESSAY, "서술형", null, "  ", 0, List.of())))
                .isInstanceOf(InvalidQuestionException.class);

        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
        then(questionRepository).should(never()).save(any(Question.class));
    }

    @Test
    @DisplayName("없거나 삭제한 문제를 수정하면 QuestionNotFoundException이다")
    void updateOfUnknownQuestion() {
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.empty());
        lenient()
                .when(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .thenReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.update(ACTOR_ID, QUESTION_ID, validRequest()))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    @DisplayName("없거나 삭제한 카테고리로 옮기는 수정은 CategoryNotFoundException이고 문제를 바꾸지 않는다")
    void updateToUnknownCategory() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        lenient().when(questionRepository.findWithChoicesById(QUESTION_ID)).thenReturn(Optional.of(question));
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull("removed"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.update(
                        ACTOR_ID,
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
        lenient()
                .when(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .thenReturn(Optional.of(java));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(true);

        assertThatThrownBy(() -> adminQuestionService.update(
                        ACTOR_ID,
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
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(java));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(true);
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.update(
                ACTOR_ID,
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
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .willReturn(Optional.of(java));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(false);
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.update(
                ACTOR_ID,
                QUESTION_ID,
                new QuestionRequest(SLUG, QuestionType.ESSAY, "서술형", null, "모범 답안", 0, List.of()));

        assertThat(response.type()).isEqualTo(QuestionType.ESSAY);
        assertThat(response.choices()).isEmpty();
    }

    // 담당 범위와 카테고리 옮기기 (R5, R6)

    @Test
    @DisplayName("원래 카테고리와 옮겨 갈 카테고리를 모두 담당하면 문제를 옮길 수 있다 (R5)")
    void movesQuestionBetweenAssignedCategories() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(JAVA_ID, SPRING_ID)));
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        given(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull("spring"))
                .willReturn(Optional.of(spring));
        given(questionAttemptRepository.existsByQuestionId(QUESTION_ID)).willReturn(false);
        given(questionRepository.saveAndFlush(any(Question.class)))
                .willAnswer(invocation -> withIds(invocation.getArgument(0)));

        AdminQuestionResponse response = adminQuestionService.update(ACTOR_ID, QUESTION_ID, validRequestIn("spring"));

        assertThat(response.categorySlug()).isEqualTo("spring");
        assertThat(question.getCategory()).isSameAs(spring);
    }

    @Test
    @DisplayName("원래 카테고리만 담당하고 옮겨 갈 카테고리를 담당하지 않으면 CategoryAccessDeniedException이고 문제를 바꾸지 않는다 (R5)")
    void rejectsMoveToUnassignedCategory() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(JAVA_ID)));
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        lenient()
                .when(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull("spring"))
                .thenReturn(Optional.of(spring));

        assertThatThrownBy(() -> adminQuestionService.update(ACTOR_ID, QUESTION_ID, validRequestIn("spring")))
                .isInstanceOf(CategoryAccessDeniedException.class);

        assertUnchanged(question);
    }

    @Test
    @DisplayName("옮겨 갈 카테고리만 담당하고 원래 카테고리를 담당하지 않으면 CategoryAccessDeniedException이고 문제를 바꾸지 않는다 (R5)")
    void rejectsMoveFromUnassignedCategory() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(SPRING_ID)));
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        lenient()
                .when(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull("spring"))
                .thenReturn(Optional.of(spring));

        assertThatThrownBy(() -> adminQuestionService.update(ACTOR_ID, QUESTION_ID, validRequestIn("spring")))
                .isInstanceOf(CategoryAccessDeniedException.class);

        assertUnchanged(question);
    }

    @Test
    @DisplayName("담당이 아닌 카테고리의 문제를 같은 카테고리 안에서 고쳐도 CategoryAccessDeniedException이다 (R6)")
    void rejectsUpdateInUnassignedCategory() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(SPRING_ID)));
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findWithChoicesById(QUESTION_ID)).willReturn(Optional.of(question));
        lenient()
                .when(categoryRepository.findWithShareLockBySlugAndDeletedAtIsNull(SLUG))
                .thenReturn(Optional.of(java));

        assertThatThrownBy(() -> adminQuestionService.update(ACTOR_ID, QUESTION_ID, validRequestIn(SLUG)))
                .isInstanceOf(CategoryAccessDeniedException.class);

        assertUnchanged(question);
    }

    @Test
    @DisplayName("문제를 삭제하면 soft delete 한다 (R6, D2)")
    void deletesQuestion() {
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findByIdAndDeletedAtIsNull(QUESTION_ID)).willReturn(Optional.of(question));

        adminQuestionService.delete(ACTOR_ID, QUESTION_ID);

        assertThat(question.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("담당이 아닌 카테고리의 문제를 삭제하면 CategoryAccessDeniedException이고 삭제하지 않는다 (R6)")
    void rejectsDeleteInUnassignedCategory() {
        given(adminScopeService.of(ACTOR_ID)).willReturn(AdminScope.categories(Set.of(SPRING_ID)));
        Question question = withIds(Question.create(java, validRequestDraft()));
        given(questionRepository.findByIdAndDeletedAtIsNull(QUESTION_ID)).willReturn(Optional.of(question));

        assertThatThrownBy(() -> adminQuestionService.delete(ACTOR_ID, QUESTION_ID))
                .isInstanceOf(CategoryAccessDeniedException.class);

        assertThat(question.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("없거나 이미 삭제한 문제를 삭제하면 QuestionNotFoundException이다")
    void deleteOfUnknownQuestion() {
        given(questionRepository.findByIdAndDeletedAtIsNull(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminQuestionService.delete(ACTOR_ID, QUESTION_ID))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    private void assertUnchanged(Question question) {
        assertThat(question.getContent()).isEqualTo("문제");
        assertThat(question.getCategory()).isSameAs(java);
        assertThat(question.getChoices()).hasSize(2);
        then(questionRepository).should(never()).flush();
        then(questionRepository).should(never()).saveAndFlush(any(Question.class));
    }

    private static QuestionRequest validRequest() {
        return validRequestIn(SLUG);
    }

    private static QuestionRequest validRequestIn(String categorySlug) {
        return new QuestionRequest(categorySlug, QuestionType.SHORT_ANSWER, "바뀐 문제", null, "모범 답안", 0, List.of());
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
