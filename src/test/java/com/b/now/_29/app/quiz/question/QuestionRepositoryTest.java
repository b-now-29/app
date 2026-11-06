package com.b.now._29.app.quiz.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class QuestionRepositoryTest {

    private static final String SLUG = "repository-test";
    private static final String OTHER_SLUG = "repository-test-other";

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("문제를 저장한 순서가 아니라 표시 순서로 조회한다")
    void sortsQuestionsBySortOrderNotInsertionOrder() {
        Category category = saveCategory(SLUG);
        saveQuestion(category, "나중에 보여 줄 문제", 20);
        saveQuestion(category, "먼저 보여 줄 문제", 10);
        entityManager.clear();

        assertThat(findMultipleChoice(SLUG))
                .extracting(Question::getContent)
                .containsExactly("먼저 보여 줄 문제", "나중에 보여 줄 문제");
    }

    @Test
    @DisplayName("목록 조회는 문제마다 보기를 표시 순서로, 같으면 id 순서로 돌려준다")
    void sortsChoicesInQuestionList() {
        Category category = saveCategory(SLUG);
        Question later = new Question(category, QuestionType.MULTIPLE_CHOICE, "나중 문제", null, 20);
        addChoicesOutOfOrder(later);
        questionRepository.saveAndFlush(later);
        Question earlier = new Question(category, QuestionType.MULTIPLE_CHOICE, "먼저 문제", null, 10);
        addChoicesOutOfOrder(earlier);
        questionRepository.saveAndFlush(earlier);
        entityManager.clear();

        List<Question> questions = findMultipleChoice(SLUG);

        assertThat(questions).extracting(Question::getContent).containsExactly("먼저 문제", "나중 문제");
        assertThat(questions).allSatisfy(question -> assertThat(question.getChoices())
                .extracting(Choice::getContent)
                .containsExactly("표시 순서 10", "표시 순서 20 (먼저 저장)", "표시 순서 20 (나중에 저장)"));
    }

    @Test
    @DisplayName("단건 조회는 보기를 표시 순서로, 같으면 id 순서로 돌려준다")
    void sortsChoicesInSingleQuestion() {
        Question question = new Question(saveCategory(SLUG), QuestionType.MULTIPLE_CHOICE, "문제", null, 10);
        addChoicesOutOfOrder(question);
        Long id = questionRepository.saveAndFlush(question).getId();
        entityManager.clear();

        assertThat(questionRepository.findWithChoicesById(id).orElseThrow().getChoices())
                .extracting(Choice::getContent)
                .containsExactly("표시 순서 10", "표시 순서 20 (먼저 저장)", "표시 순서 20 (나중에 저장)");
    }

    @Test
    @DisplayName("객관식이 아닌 문제는 조회하지 않는다")
    void findsOnlyMultipleChoiceQuestions() {
        saveQuestion(saveCategory(SLUG), "객관식", 20);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        assertThat(findMultipleChoice(SLUG)).extracting(Question::getContent).containsExactly("객관식");
    }

    @Test
    @DisplayName("요청한 유형들을 유형과 상관없이 표시 순서로 함께 조회하고, 요청하지 않은 유형은 뺀다 (R1)")
    void findsRequestedTypesTogetherInSortOrder() {
        saveQuestion(saveCategory(SLUG), "객관식", 20);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        assertThat(findMultipleChoiceAndShortAnswer(SLUG))
                .extracting(Question::getContent, Question::getType)
                .containsExactly(tuple("주관식", QuestionType.SHORT_ANSWER), tuple("객관식", QuestionType.MULTIPLE_CHOICE));
    }

    // 목록 조회가 inner join이면 보기가 없는 주관식 문제가 결과에서 빠진다
    @Test
    @DisplayName("보기가 없는 문제도 결과에 포함하고 보기는 빈 목록으로 불러온다 (R2)")
    void includesQuestionWithoutChoices() {
        saveCategory(SLUG);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        List<Question> questions = findMultipleChoiceAndShortAnswer(SLUG);

        assertThat(questions).extracting(Question::getContent).containsExactly("주관식");
        assertThat(isChoicesLoaded(questions.getFirst())).isTrue();
        assertThat(questions.getFirst().getChoices()).isEmpty();
    }

    @Test
    @DisplayName("보기가 없는 문제와 함께 조회해도 객관식 문제는 중복 없이 보기를 표시 순서로 돌려준다 (R10)")
    void keepsChoicesOfMultipleChoiceWhenMixedWithShortAnswer() {
        Question multipleChoice = new Question(saveCategory(SLUG), QuestionType.MULTIPLE_CHOICE, "객관식", null, 20);
        addChoicesOutOfOrder(multipleChoice);
        questionRepository.saveAndFlush(multipleChoice);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        List<Question> questions = findMultipleChoiceAndShortAnswer(SLUG);

        assertThat(questions).extracting(Question::getContent).containsExactly("주관식", "객관식");
        assertThat(questions.getLast().getChoices())
                .extracting(Choice::getContent)
                .containsExactly("표시 순서 10", "표시 순서 20 (먼저 저장)", "표시 순서 20 (나중에 저장)");
    }

    @Test
    @DisplayName("다른 카테고리의 문제는 조회하지 않는다")
    void findsOnlyQuestionsOfCategory() {
        saveQuestion(saveCategory(SLUG), "이 카테고리의 문제", 10);
        saveQuestion(saveCategory(OTHER_SLUG), "다른 카테고리의 문제", 10);
        entityManager.clear();

        assertThat(findMultipleChoice(SLUG)).extracting(Question::getContent).containsExactly("이 카테고리의 문제");
    }

    @Test
    @DisplayName("문제가 없는 카테고리는 빈 목록을 돌려준다")
    void returnsEmptyListForCategoryWithoutQuestions() {
        saveCategory(SLUG);
        entityManager.clear();

        assertThat(findMultipleChoice(SLUG)).isEmpty();
    }

    @Test
    @DisplayName("목록 조회는 보기를 함께 불러온다")
    void loadsChoicesWithQuestionList() {
        Category category = saveCategory(SLUG);
        saveQuestion(category, "문제 1", 10);
        saveQuestion(category, "문제 2", 20);
        entityManager.clear();

        // 테스트 트랜잭션 안에서는 지연 로딩도 성공하므로, 보기에 접근하기 전에 이미 불러왔는지 확인한다
        assertThat(findMultipleChoice(SLUG)).hasSize(2).allMatch(this::isChoicesLoaded);
    }

    @Test
    @DisplayName("단건 조회는 보기를 함께 불러온다")
    void loadsChoicesWithSingleQuestion() {
        Long id = saveQuestion(saveCategory(SLUG), "문제", 10).getId();
        entityManager.clear();

        Question found = questionRepository.findWithChoicesById(id).orElseThrow();

        assertThat(isChoicesLoaded(found)).isTrue();
    }

    @Test
    @DisplayName("없는 문제를 단건 조회하면 빈 Optional을 돌려준다")
    void returnsEmptyForUnknownQuestion() {
        assertThat(questionRepository.findWithChoicesById(Long.MAX_VALUE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"java", "spring", "database"})
    @DisplayName("시드 카테고리에는 객관식 3문제가 있고, 모든 문제는 보기 4개와 정답 1개를 가진다")
    void seedHasThreeQuestionsWithFourChoicesAndOneCorrect(String slug) {
        List<Question> questions = findMultipleChoice(slug);

        assertThat(questions).hasSize(3).allSatisfy(question -> {
            assertThat(question.getChoices()).hasSize(4);
            assertThat(question.getChoices()).filteredOn(Choice::isCorrect).hasSize(1);
        });
    }

    // 객관식 시드 검사는 위 테스트가 맡고, 여기서는 V6가 더한 주관식 문제와 그 위치를 확인한다
    @ParameterizedTest
    @ValueSource(strings = {"java", "spring", "database"})
    @DisplayName("시드 카테고리에는 주관식 1문제가 객관식 뒤에 있고, 보기 없이 모범 답안을 가진다 (R9)")
    void seedHasOneShortAnswerAfterMultipleChoice(String slug) {
        List<Question> questions = findMultipleChoiceAndShortAnswer(slug);

        assertThat(questions)
                .extracting(Question::getType)
                .containsExactly(
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.SHORT_ANSWER);
        assertThat(questions.getLast().getChoices()).isEmpty();
        assertThat(modelAnswerOf(questions.getLast())).isNotBlank();
    }

    // 위치와 개수를 함께 보려고 세 유형을 모두 조회한다. 위의 시드 테스트는 조회 유형을 좁혀 두어 V7의 영향을 받지 않는다
    @ParameterizedTest
    @ValueSource(strings = {"java", "spring", "database"})
    @DisplayName("시드 카테고리에는 서술형 1문제가 주관식 뒤에 있고, 보기 없이 모범 답안을 가진다 (R6)")
    void seedHasOneEssayAfterShortAnswer(String slug) {
        List<Question> questions = findAllTypes(slug);

        assertThat(questions)
                .extracting(Question::getType)
                .containsExactly(
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.SHORT_ANSWER,
                        QuestionType.ESSAY);
        assertThat(questions.getLast().getChoices()).isEmpty();
        assertThat(modelAnswerOf(questions.getLast())).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"java", "spring", "database"})
    @DisplayName("서술형 시드의 표시 순서는 50이다 (R6)")
    void seedEssayHasSortOrderFifty(String slug) {
        List<Question> essays = findEssay(slug);

        assertThat(essays).hasSize(1);
        assertThat(ReflectionTestUtils.getField(essays.getFirst(), "sortOrder")).isEqualTo(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"java", "spring", "database"})
    @DisplayName("서술형 시드의 문제와 모범 답안에는 마크다운이 없다 (D3)")
    void seedEssayHasNoMarkdown(String slug) {
        List<Question> essays = findEssay(slug);

        assertThat(essays).hasSize(1);
        assertThat(List.of(essays.getFirst().getContent(), modelAnswerOf(essays.getFirst())))
                .allSatisfy(text -> assertThat(text)
                        .doesNotContain("`", "**", "](")
                        .doesNotContainPattern("(?m)^\\s*(?:#|[-*+]\\s)"));
    }

    @Test
    @DisplayName("서술형도 요청한 유형에 넣으면 다른 유형과 함께 표시 순서로 조회한다 (R1)")
    void findsEssayTogetherInSortOrder() {
        saveQuestion(saveCategory(SLUG), "객관식", 20);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        assertThat(findAllTypes(SLUG))
                .extracting(Question::getContent, Question::getType)
                .containsExactly(
                        tuple("주관식", QuestionType.SHORT_ANSWER),
                        tuple("객관식", QuestionType.MULTIPLE_CHOICE),
                        tuple("서술형", QuestionType.ESSAY));
    }

    @Test
    @DisplayName("보기가 없는 서술형도 결과에 포함하고 보기는 빈 목록으로 불러온다 (R2)")
    void includesEssayWithoutChoices() {
        saveCategory(SLUG);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        List<Question> essays = findEssay(SLUG);

        assertThat(essays).extracting(Question::getContent).containsExactly("서술형");
        assertThat(isChoicesLoaded(essays.getFirst())).isTrue();
        assertThat(essays.getFirst().getChoices()).isEmpty();
    }

    @Test
    @DisplayName("model_answer 컬럼을 모범 답안 필드로 읽는다")
    void mapsModelAnswerColumn() {
        saveCategory(SLUG);
        insertShortAnswerAndEssay(SLUG);
        entityManager.clear();

        Question found = findMultipleChoiceAndShortAnswer(SLUG).getFirst();

        assertThat(modelAnswerOf(found)).isEqualTo("모범 답안");
    }

    @Test
    @DisplayName("한 문제에 정답 보기를 둘 저장할 수 없다")
    void rejectsSecondCorrectChoice() {
        Question question = new Question(saveCategory(SLUG), QuestionType.MULTIPLE_CHOICE, "문제", null, 10);
        question.addChoice("첫 번째 정답", true, 10);
        question.addChoice("두 번째 정답", true, 20);

        // 제약 이름까지 확인해서 매핑 오류로 인한 다른 제약 위반이 통과로 잡히지 않게 한다
        assertThatThrownBy(() -> questionRepository.saveAndFlush(question))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_choice_one_correct");
    }

    // 관리자 기능: soft delete (R6, D2)와 수정 (D6)

    @Test
    @DisplayName("공개 문제 목록은 삭제한 문제를 뺀다 (R6)")
    void questionListExcludesDeletedQuestion() {
        Category category = saveCategory(SLUG);
        saveQuestion(category, "남은 문제", 10);
        deleteQuestion(saveQuestion(category, "삭제한 문제", 20));
        entityManager.clear();

        assertThat(findAllTypes(SLUG)).extracting(Question::getContent).containsExactly("남은 문제");
    }

    @Test
    @DisplayName("보기와 함께 단건 조회하면 삭제한 문제는 빈 Optional이다 (R6)")
    void findWithChoicesByIdExcludesDeletedQuestion() {
        Long id = deleteQuestion(saveQuestion(saveCategory(SLUG), "삭제한 문제", 10));
        entityManager.clear();

        assertThat(questionRepository.findWithChoicesById(id)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndDeletedAtIsNull은 남은 문제만 찾는다 (R6: 모범 답안·자기 채점)")
    void findsOnlyActiveQuestionById() {
        Category category = saveCategory(SLUG);
        Long active = saveQuestion(category, "남은 문제", 10).getId();
        Long deleted = deleteQuestion(saveQuestion(category, "삭제한 문제", 20));
        entityManager.clear();

        assertThat(questionRepository.findByIdAndDeletedAtIsNull(active)).isPresent();
        assertThat(questionRepository.findByIdAndDeletedAtIsNull(deleted)).isEmpty();
    }

    @Test
    @DisplayName("countByCategoryAndDeletedAtIsNull은 삭제 안 된 문제만 센다 (R7)")
    void countsOnlyActiveQuestionsOfCategory() {
        Category category = saveCategory(SLUG);
        saveQuestion(category, "남은 문제 1", 10);
        saveQuestion(category, "남은 문제 2", 20);
        deleteQuestion(saveQuestion(category, "삭제한 문제", 30));
        saveQuestion(saveCategory(OTHER_SLUG), "다른 카테고리 문제", 10);

        assertThat(questionRepository.countByCategoryAndDeletedAtIsNull(category))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("삭제하면 deleted_at에 시각이 들어가고 행은 남는다 (D2)")
    void mapsDeletedAtColumn() {
        Long id = deleteQuestion(saveQuestion(saveCategory(SLUG), "삭제한 문제", 10));
        entityManager.clear();

        Object deletedAt = entityManager
                .createNativeQuery("SELECT deleted_at FROM question WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(deletedAt).isNotNull();
        assertThat(questionRepository.findById(id))
                .hasValueSatisfying(found -> assertThat(found.isDeleted()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("팩토리로 만든 주관식·서술형은 모범 답안 제약을 지켜 저장되고 model_answer를 읽는다 (R5)")
    void savesTextQuestionCreatedByFactory(String type) {
        Question question = questionRepository.saveAndFlush(Question.create(
                saveCategory(SLUG), new QuestionDraft(QuestionType.valueOf(type), "문제", "해설", "모범 답안", 10, List.of())));
        entityManager.clear();

        Question found =
                questionRepository.findWithChoicesById(question.getId()).orElseThrow();
        assertThat(found.getModelAnswer()).isEqualTo("모범 답안");
        assertThat(found.getExplanation()).isEqualTo("해설");
        assertThat(found.getChoices()).isEmpty();
    }

    // 보기를 통째로 바꿀 때 새 정답 INSERT가 옛 정답 DELETE보다 먼저 나가면 uq_choice_one_correct에 걸린다
    @Test
    @DisplayName("정답이 바뀌도록 보기를 통째로 교체해도 제약 위반 없이 저장되고 새 보기만 남는다 (D6)")
    void replacesChoicesWithDifferentCorrectChoice() {
        Category category = saveCategory(SLUG);
        Question question = questionRepository.saveAndFlush(Question.create(
                category,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        "문제",
                        null,
                        null,
                        10,
                        List.of(new ChoiceDraft("옛 정답", true), new ChoiceDraft("옛 오답", false)))));
        entityManager.clear();

        Question loaded =
                questionRepository.findWithChoicesById(question.getId()).orElseThrow();
        // AdminQuestionService.update와 같은 순서 (D16): 옛 보기 DELETE를 먼저 반영해야 uq_choice_one_correct를 어기지 않는다
        loaded.clearChoices();
        questionRepository.flush();
        loaded.update(
                category,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        "문제",
                        null,
                        null,
                        10,
                        List.of(new ChoiceDraft("새 오답", false), new ChoiceDraft("새 정답", true))));
        questionRepository.saveAndFlush(loaded);
        entityManager.clear();

        assertThat(questionRepository
                        .findWithChoicesById(question.getId())
                        .orElseThrow()
                        .getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("새 오답", false), tuple("새 정답", true));
        assertThat(entityManager
                        .createNativeQuery("SELECT count(*) FROM choice WHERE question_id = :id")
                        .setParameter("id", question.getId())
                        .getSingleResult())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("객관식을 서술형으로 바꾸면 보기 행이 지워지고 모범 답안이 저장된다 (D6)")
    void changesTypeAndRemovesChoiceRows() {
        Category category = saveCategory(SLUG);
        Long id = saveQuestion(category, "객관식", 10).getId();
        entityManager.clear();

        Question loaded = questionRepository.findWithChoicesById(id).orElseThrow();
        loaded.update(category, new QuestionDraft(QuestionType.ESSAY, "서술형", null, "모범 답안", 10, List.of()));
        questionRepository.saveAndFlush(loaded);
        entityManager.clear();

        Question found = questionRepository.findWithChoicesById(id).orElseThrow();
        assertThat(found.getType()).isEqualTo(QuestionType.ESSAY);
        assertThat(found.getChoices()).isEmpty();
        assertThat(found.getModelAnswer()).isEqualTo("모범 답안");
    }

    private Long deleteQuestion(Question question) {
        question.delete();
        return questionRepository.saveAndFlush(question).getId();
    }

    private List<Question> findMultipleChoice(String slug) {
        return questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(
                slug, List.of(QuestionType.MULTIPLE_CHOICE));
    }

    private List<Question> findMultipleChoiceAndShortAnswer(String slug) {
        return questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(
                slug, List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER));
    }

    private List<Question> findAllTypes(String slug) {
        return questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(
                slug, List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY));
    }

    private List<Question> findEssay(String slug) {
        return questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(
                slug, List.of(QuestionType.ESSAY));
    }

    private Category saveCategory(String slug) {
        return categoryRepository.saveAndFlush(new Category(slug, "테스트 카테고리", 1000));
    }

    private Question saveQuestion(Category category, String content, int sortOrder) {
        Question question = new Question(category, QuestionType.MULTIPLE_CHOICE, content, null, sortOrder);
        question.addChoice("정답", true, 10);
        question.addChoice("오답", false, 20);
        return questionRepository.saveAndFlush(question);
    }

    private static void addChoicesOutOfOrder(Question question) {
        question.addChoice("표시 순서 20 (먼저 저장)", false, 20);
        question.addChoice("표시 순서 10", true, 10);
        question.addChoice("표시 순서 20 (나중에 저장)", false, 20);
    }

    /** 생성자가 모범 답안을 받지 않으므로 chk_question_model_answer를 만족하는 행은 SQL로 넣는다. */
    private void insertShortAnswerAndEssay(String slug) {
        entityManager.createNativeQuery("""
                        INSERT INTO question (category_id, type, content, model_answer, sort_order)
                        SELECT category.id, v.type, v.content, '모범 답안', v.sort_order
                        FROM category CROSS JOIN (VALUES ('SHORT_ANSWER', '주관식', 10),
                                                         ('ESSAY', '서술형', 30)) AS v (type, content, sort_order)
                        WHERE category.slug = :slug
                        """).setParameter("slug", slug).executeUpdate();
    }

    /** 공개 경로인 revealModelAnswer()는 도메인 규칙까지 함께 타므로, 매핑만 보도록 필드를 직접 읽는다. */
    private static String modelAnswerOf(Question question) {
        return (String) ReflectionTestUtils.getField(question, "modelAnswer");
    }

    private boolean isChoicesLoaded(Question question) {
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(question, "choices");
    }
}
