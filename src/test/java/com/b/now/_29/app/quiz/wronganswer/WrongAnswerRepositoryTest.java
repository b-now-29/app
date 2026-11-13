package com.b.now._29.app.quiz.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.attempt.QuestionAttempt;
import com.b.now._29.app.quiz.attempt.QuestionAttemptRepository;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.User;
import com.b.now._29.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 구현할 API (D3: question_attempt만 조회한다).
 *
 * <ul>
 *   <li>{@code List<WrongAnswerRow> findWrongAnswers(long userId)}: 카테고리 sort_order 순, 카테고리 안에서는 attempted_at DESC, id
 *       DESC
 *   <li>{@code List<Question> findWrongQuestions(long userId, String slug)}: 보기를 함께 불러오고 sort_order, id 순 (D7)
 * </ul>
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class WrongAnswerRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    WrongAnswerRepository wrongAnswerRepository;

    @Autowired
    QuestionAttemptRepository attemptRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @PersistenceContext
    EntityManager entityManager;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = userRepository.saveAndFlush(new User("wrong-me@example.com", "$2a$10$hash", new Nickname("오답나"), 1));
        other = userRepository.saveAndFlush(new User("wrong-other@example.com", "$2a$10$hash", new Nickname("오답남"), 1));
    }

    // 오답 목록

    @Test
    @DisplayName("기록이 없으면 오답 목록은 빈 목록이다")
    void wrongAnswersAreEmptyWithoutAttempts() {
        saveQuestion(saveCategory("wrong-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("오답 행은 카테고리 slug·이름과 문제 id·본문·유형, 마지막으로 푼 시각을 담는다")
    void mapsWrongAnswerRow() {
        Question question = saveQuestion(saveCategory("wrong-a", "카테고리 A", 1000), QuestionType.ESSAY, "서술형");
        attempt(me, question, false, BASE);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .containsExactly(
                        new WrongAnswerRow("wrong-a", "카테고리 A", question.getId(), "서술형", QuestionType.ESSAY, BASE));
    }

    @Test
    @DisplayName("문제마다 가장 최근 기록이 틀림인 문제만 오답이다. id가 아니라 시각으로 최근을 정한다 (R1, D1)")
    void wrongAnswerIsLatestAttemptWrong() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Question wrongLater = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "나중에 틀린 문제");
        Question correctLater = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "나중에 맞힌 문제");
        // 먼저 저장해 id가 작지만 시각은 더 나중인 기록이 최근이다
        attempt(me, wrongLater, false, BASE.plusSeconds(120));
        attempt(me, wrongLater, true, BASE.plusSeconds(60));
        attempt(me, correctLater, true, BASE.plusSeconds(120));
        attempt(me, correctLater, false, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.questionId()).isEqualTo(wrongLater.getId());
                    assertThat(row.attemptedAt()).isEqualTo(BASE.plusSeconds(120));
                });
    }

    @Test
    @DisplayName("한 문제의 기록 시각이 같으면 id가 큰 기록이 최근이다 (R1)")
    void breaksTieOfAttemptedAtById() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Question wrongLast = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "마지막에 틀림");
        Question correctLast = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "마지막에 맞힘");
        attempt(me, wrongLast, true, BASE);
        attempt(me, wrongLast, false, BASE);
        attempt(me, correctLast, false, BASE);
        attempt(me, correctLast, true, BASE);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .extracting(WrongAnswerRow::questionId)
                .containsExactly(wrongLast.getId());
    }

    @Test
    @DisplayName("다시 풀어 맞히면 오답에서 빠지고, 다시 틀리면 남는다 (R2)")
    void retryRemovesOrKeepsWrongAnswer() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Question fixed = saveQuestion(category, QuestionType.SHORT_ANSWER, "다시 맞힌 문제");
        Question stillWrong = saveQuestion(category, QuestionType.ESSAY, "다시 틀린 문제");
        attempt(me, fixed, false, BASE);
        attempt(me, stillWrong, false, BASE);
        attempt(me, fixed, true, BASE.plusSeconds(60));
        attempt(me, stillWrong, false, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.questionId()).isEqualTo(stillWrong.getId());
                    assertThat(row.attemptedAt()).isEqualTo(BASE.plusSeconds(60));
                });
    }

    @Test
    @DisplayName("다른 회원의 기록은 오답 판정에 쓰지 않는다 (R4)")
    void excludesAttemptsOfOtherUsers() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Question shared = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "같이 푼 문제");
        Question othersWrong = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "다른 회원만 틀린 문제");
        Question myFixed = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "내가 맞힌 문제");
        attempt(me, shared, false, BASE);
        // 다른 회원의 더 최근 정답이 내 오답을 지우면 안 된다
        attempt(other, shared, true, BASE.plusSeconds(60));
        attempt(other, othersWrong, false, BASE);
        attempt(me, myFixed, true, BASE);
        // 다른 회원의 더 최근 오답이 내 정답을 덮으면 안 된다
        attempt(other, myFixed, false, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .extracting(WrongAnswerRow::questionId)
                .containsExactly(shared.getId());
        assertThat(wrongAnswerRepository.findWrongAnswers(other.getId()))
                .extracting(WrongAnswerRow::questionId)
                .containsExactlyInAnyOrder(othersWrong.getId(), myFixed.getId());
    }

    @Test
    @DisplayName("삭제된 문제와 삭제된 카테고리의 문제는 오답 목록에 나오지 않는다 (R3)")
    void excludesDeletedQuestionAndCategory() {
        Category alive = saveCategory("wrong-a", "카테고리 A", 1000);
        Category deletedCategory = saveCategory("wrong-b", "카테고리 B", 2000);
        Question kept = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "남는 문제");
        Question deletedQuestion = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "삭제한 문제");
        Question inDeletedCategory = saveQuestion(deletedCategory, QuestionType.ESSAY, "삭제한 카테고리 문제");
        attempt(me, kept, false, BASE);
        attempt(me, deletedQuestion, false, BASE);
        attempt(me, inDeletedCategory, false, BASE);
        deleteQuestion(deletedQuestion);
        deleteCategory(deletedCategory);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .extracting(WrongAnswerRow::questionId)
                .containsExactly(kept.getId());
    }

    @Test
    @DisplayName("카테고리 sort_order 순이고, 카테고리 안에서는 마지막으로 푼 시각 최신순, 같으면 기록 id 큰 순이다")
    void sortsByCategoryThenLatestAttempt() {
        Category third = saveCategory("wrong-c", "카테고리 C", 3000);
        Category first = saveCategory("wrong-a", "카테고리 A", 1000);
        Category second = saveCategory("wrong-b", "카테고리 B", 2000);
        Question c1 = saveQuestion(third, QuestionType.MULTIPLE_CHOICE, "C1");
        Question a1 = saveQuestion(first, QuestionType.MULTIPLE_CHOICE, "A1");
        Question a2 = saveQuestion(first, QuestionType.MULTIPLE_CHOICE, "A2");
        Question a3 = saveQuestion(first, QuestionType.MULTIPLE_CHOICE, "A3");
        Question a4 = saveQuestion(first, QuestionType.MULTIPLE_CHOICE, "A4");
        Question b1 = saveQuestion(second, QuestionType.MULTIPLE_CHOICE, "B1");
        attempt(me, c1, false, BASE.plusSeconds(500));
        attempt(me, a1, false, BASE);
        attempt(me, a2, false, BASE.plusSeconds(120));
        // a3, a4는 시각이 같아 기록 id가 큰 a4가 먼저다
        attempt(me, a3, false, BASE.plusSeconds(60));
        attempt(me, a4, false, BASE.plusSeconds(60));
        attempt(me, b1, false, BASE);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongAnswers(me.getId()))
                .extracting(WrongAnswerRow::questionId)
                .containsExactly(a2.getId(), a4.getId(), a3.getId(), a1.getId(), b1.getId(), c1.getId());
    }

    // 다시 풀기용 문제

    @Test
    @DisplayName("다시 풀기용 문제는 그 카테고리에서 가장 최근 기록이 틀림인 문제만 돌려준다 (R1, R2, D4)")
    void wrongQuestionsAreOnlyWrongInCategory() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Category otherCategory = saveCategory("wrong-b", "카테고리 B", 2000);
        Question wrong = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "틀린 문제");
        Question fixed = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "다시 맞힌 문제");
        saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "안 푼 문제");
        Question elsewhere = saveQuestion(otherCategory, QuestionType.MULTIPLE_CHOICE, "다른 카테고리 오답");
        Question tie = saveQuestion(category, QuestionType.ESSAY, "같은 시각 마지막에 틀림");
        attempt(me, wrong, false, BASE);
        attempt(me, fixed, false, BASE);
        attempt(me, fixed, true, BASE.plusSeconds(60));
        attempt(me, elsewhere, false, BASE);
        attempt(me, tie, true, BASE);
        attempt(me, tie, false, BASE);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongQuestions(me.getId(), "wrong-a"))
                .extracting(Question::getId)
                .containsExactlyInAnyOrder(wrong.getId(), tie.getId());
    }

    @Test
    @DisplayName("다시 풀기용 문제는 다른 회원의 기록으로 판정하지 않는다 (R4)")
    void wrongQuestionsAreOnlyMine() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Question mine = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "내 오답");
        Question others = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "다른 회원 오답");
        attempt(me, mine, false, BASE);
        attempt(other, mine, true, BASE.plusSeconds(60));
        attempt(other, others, false, BASE);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongQuestions(me.getId(), "wrong-a"))
                .extracting(Question::getId)
                .containsExactly(mine.getId());
    }

    @Test
    @DisplayName("다시 풀기용 문제는 기존 문제 API처럼 sort_order, id 순이고 최근에 틀린 순이 아니다 (D7)")
    void sortsWrongQuestionsLikeQuestionApi() {
        Category category = saveCategory("wrong-a", "카테고리 A", 1000);
        Question late = saveMultipleChoice(category, "sort 30", 30);
        Question early = saveMultipleChoice(category, "sort 10", 10);
        Question tieFirst = saveMultipleChoice(category, "sort 20 먼저", 20);
        Question tieSecond = saveMultipleChoice(category, "sort 20 나중", 20);
        attempt(me, early, false, BASE.plusSeconds(300));
        attempt(me, tieSecond, false, BASE.plusSeconds(200));
        attempt(me, late, false, BASE.plusSeconds(100));
        attempt(me, tieFirst, false, BASE);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongQuestions(me.getId(), "wrong-a"))
                .extracting(Question::getId)
                .containsExactly(early.getId(), tieFirst.getId(), tieSecond.getId(), late.getId());
    }

    @Test
    @DisplayName("다시 풀기용 문제에서 삭제된 문제는 빠지고, 삭제된 카테고리는 빈 목록이다 (R3)")
    void wrongQuestionsExcludeDeleted() {
        Category alive = saveCategory("wrong-a", "카테고리 A", 1000);
        Category deletedCategory = saveCategory("wrong-b", "카테고리 B", 2000);
        Question kept = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "남는 문제");
        Question deletedQuestion = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "삭제한 문제");
        Question inDeletedCategory = saveQuestion(deletedCategory, QuestionType.MULTIPLE_CHOICE, "삭제한 카테고리 문제");
        attempt(me, kept, false, BASE);
        attempt(me, deletedQuestion, false, BASE);
        attempt(me, inDeletedCategory, false, BASE);
        deleteQuestion(deletedQuestion);
        deleteCategory(deletedCategory);
        entityManager.clear();

        assertThat(wrongAnswerRepository.findWrongQuestions(me.getId(), "wrong-a"))
                .extracting(Question::getId)
                .containsExactly(kept.getId());
        assertThat(wrongAnswerRepository.findWrongQuestions(me.getId(), "wrong-b"))
                .isEmpty();
    }

    @Test
    @DisplayName("다시 풀기용 문제는 보기를 함께 불러온다 (N+1 방지)")
    void loadsChoicesWithWrongQuestions() {
        Question question = saveQuestion(saveCategory("wrong-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        attempt(me, question, false, BASE);
        entityManager.clear();

        List<Question> found = wrongAnswerRepository.findWrongQuestions(me.getId(), "wrong-a");

        // 테스트 트랜잭션 안에서는 지연 로딩도 성공하므로, 접근하기 전에 이미 불러왔는지 확인한다
        assertThat(found).hasSize(1);
        assertThat(isLoaded(found.getFirst(), "choices")).isTrue();
        assertThat(found.getFirst().getChoices()).hasSize(2);
    }

    private Category saveCategory(String slug, String name, int sortOrder) {
        return categoryRepository.saveAndFlush(new Category(slug, name, sortOrder));
    }

    private void deleteQuestion(Question question) {
        Question managed = questionRepository.findById(question.getId()).orElseThrow();
        managed.delete();
        questionRepository.saveAndFlush(managed);
    }

    /** 문제 수 검증은 삭제 대상이 아니므로 남은 문제 0개로 지운다. 문제 행은 그대로 둔다 (관리자 R6). */
    private void deleteCategory(Category category) {
        Category managed = categoryRepository.findById(category.getId()).orElseThrow();
        managed.delete(0);
        categoryRepository.saveAndFlush(managed);
    }

    private Question saveMultipleChoice(Category category, String content, int sortOrder) {
        Question question = new Question(category, QuestionType.MULTIPLE_CHOICE, content, null, sortOrder);
        question.addChoice("정답", true, 10);
        question.addChoice("오답", false, 20);
        return questionRepository.saveAndFlush(question);
    }

    /** 생성자가 모범 답안을 받지 않으므로 chk_question_model_answer를 만족하는 주관식·서술형은 SQL로 넣는다. */
    private Question saveQuestion(Category category, QuestionType type, String content) {
        if (type == QuestionType.MULTIPLE_CHOICE) {
            return saveMultipleChoice(category, content, 10);
        }
        entityManager
                .createNativeQuery("""
                        INSERT INTO question (category_id, type, content, model_answer, sort_order)
                        SELECT id, :type, :content, '모범 답안', 10 FROM category WHERE slug = :slug
                        """)
                .setParameter("type", type.name())
                .setParameter("content", content)
                .setParameter("slug", category.getSlug())
                .executeUpdate();
        return questionRepository
                .findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(category.getSlug(), List.of(type))
                .stream()
                .filter(question -> question.getContent().equals(content))
                .findFirst()
                .orElseThrow();
    }

    /** 기록을 저장하고 시각을 정해 둔 값으로 바꾼다. 저장한 순서대로 id가 커진다. */
    private long attempt(User user, Question question, boolean correct, Instant attemptedAt) {
        QuestionAttempt attempt = question.getType() == QuestionType.MULTIPLE_CHOICE
                ? QuestionAttempt.graded(user, question, correct)
                : QuestionAttempt.selfGraded(user, question, correct);
        long id = attemptRepository.saveAndFlush(attempt).getId();
        entityManager
                .createNativeQuery("UPDATE question_attempt SET attempted_at = :at WHERE id = :id")
                .setParameter("at", OffsetDateTime.ofInstant(attemptedAt, ZoneOffset.UTC))
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }

    private boolean isLoaded(Object entity, String attribute) {
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(entity, attribute);
    }
}
