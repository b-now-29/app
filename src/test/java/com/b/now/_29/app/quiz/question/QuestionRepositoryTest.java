package com.b.now._29.app.quiz.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private List<Question> findMultipleChoice(String slug) {
        return questionRepository.findAllByCategorySlugAndTypeOrderBySortOrderAscIdAsc(
                slug, QuestionType.MULTIPLE_CHOICE);
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

    /** model_answer를 매핑하지 않으므로 chk_question_model_answer를 만족하는 행은 SQL로 넣는다. */
    private void insertShortAnswerAndEssay(String slug) {
        entityManager.createNativeQuery("""
                        INSERT INTO question (category_id, type, content, model_answer, sort_order)
                        SELECT category.id, v.type, v.content, '모범 답안', v.sort_order
                        FROM category CROSS JOIN (VALUES ('SHORT_ANSWER', '주관식', 10),
                                                         ('ESSAY', '서술형', 30)) AS v (type, content, sort_order)
                        WHERE category.slug = :slug
                        """).setParameter("slug", slug).executeUpdate();
    }

    private boolean isChoicesLoaded(Question question) {
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(question, "choices");
    }
}
