package com.b.now._29.app.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.user.User;
import com.b.now._29.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class QuestionAttemptRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");
    private static final int RECENT_LIMIT = 20;

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
        me = userRepository.saveAndFlush(new User("attempt-me@example.com", "$2a$10$hash"));
        other = userRepository.saveAndFlush(new User("attempt-other@example.com", "$2a$10$hash"));
    }

    // 매핑

    @Test
    @DisplayName("풀이 기록의 회원, 문제, 맞음 여부를 저장하고 시각은 DB 기본값 now()로 채운다")
    void mapsQuestionAttempt() {
        Question question = saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.ESSAY, "서술형");
        Instant before = Instant.now();
        Long id = attemptRepository
                .saveAndFlush(QuestionAttempt.selfGraded(me, question, true))
                .getId();
        entityManager.clear();

        QuestionAttempt found = attemptRepository.findById(id).orElseThrow();

        assertThat(found.getUser().getId()).isEqualTo(me.getId());
        assertThat(found.getQuestion().getId()).isEqualTo(question.getId());
        assertThat(found.isCorrect()).isTrue();
        // DB 시계와 JVM 시계가 조금 다를 수 있어 넉넉히 잡는다
        assertThat(found.getAttemptedAt()).isCloseTo(before, within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("같은 문제를 다시 풀면 기존 기록을 덮어쓰지 않고 새 기록을 추가한다 (R3)")
    void keepsEveryAttemptOfSameQuestion() {
        Question question =
                saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        long first = attempt(me, question, false, BASE);
        long second = attempt(me, question, true, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(attemptRepository.findAllById(List.of(first, second)))
                .extracting(QuestionAttempt::isCorrect)
                .containsExactlyInAnyOrder(false, true);
    }

    // 인덱스

    /** 데이터가 적은 테스트에서는 플래너가 인덱스를 고르지 않아 실행 계획 대신 정의를 확인한다. 이름은 묶지 않는다. */
    @Test
    @DisplayName("문제별 최근 기록 서브쿼리용 인덱스 (user_id, question_id, attempted_at DESC, id DESC)가 있다 (V10)")
    void hasIndexForLatestAttemptPerQuestion() {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT indexdef FROM pg_indexes
                        WHERE schemaname = current_schema() AND tablename = 'question_attempt'
                        """).getResultList();
        List<String> definitions = rows.stream().map(String::valueOf).toList();

        assertThat(definitions).anySatisfy(definition -> assertThat(definition)
                .endsWith("(user_id, question_id, attempted_at DESC, id DESC)"));
    }

    // 카테고리 × 유형별 집계

    @Test
    @DisplayName("기록이 없으면 집계 결과가 빈 목록이다")
    void statsAreEmptyWithoutAttempts() {
        saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        entityManager.clear();

        assertThat(attemptRepository.findCategoryTypeStats(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("문제마다 가장 최근 기록만 센다. id 순서가 아니라 시각 순서로 최근을 정한다 (R6, D3)")
    void countsOnlyLatestAttemptPerQuestion() {
        Category category = saveCategory("attempt-a", "카테고리 A", 1000);
        Question first = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식 1");
        Question second = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식 2");
        // 먼저 저장해 id가 작지만 시각은 더 나중인 기록이 최근이다
        attempt(me, first, true, BASE.plusSeconds(120));
        attempt(me, first, false, BASE.plusSeconds(60));
        attempt(me, second, true, BASE);
        attempt(me, second, false, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(attemptRepository.findCategoryTypeStats(me.getId()))
                .containsExactly(new CategoryTypeStat("attempt-a", "카테고리 A", 1000, QuestionType.MULTIPLE_CHOICE, 2, 1));
    }

    @Test
    @DisplayName("한 문제의 기록 시각이 같으면 id가 큰 기록이 최근이다 (R6)")
    void breaksTieOfAttemptedAtById() {
        Question question =
                saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        attempt(me, question, true, BASE);
        attempt(me, question, false, BASE);
        entityManager.clear();

        assertThat(attemptRepository.findCategoryTypeStats(me.getId()))
                .containsExactly(new CategoryTypeStat("attempt-a", "카테고리 A", 1000, QuestionType.MULTIPLE_CHOICE, 1, 0));
    }

    @Test
    @DisplayName("다른 회원의 기록은 세지 않고, 다른 회원만 푼 카테고리는 결과에 없다 (R8)")
    void excludesAttemptsOfOtherUsers() {
        Category mine = saveCategory("attempt-a", "카테고리 A", 1000);
        Category othersOnly = saveCategory("attempt-b", "카테고리 B", 2000);
        Question shared = saveQuestion(mine, QuestionType.MULTIPLE_CHOICE, "같이 푼 문제");
        Question notMine = saveQuestion(mine, QuestionType.MULTIPLE_CHOICE, "다른 회원만 푼 문제");
        Question otherCategory = saveQuestion(othersOnly, QuestionType.MULTIPLE_CHOICE, "다른 카테고리 문제");
        attempt(me, shared, false, BASE);
        // 다른 회원의 더 최근 기록이 내 최근 기록으로 잡히면 안 된다
        attempt(other, shared, true, BASE.plusSeconds(60));
        attempt(other, notMine, true, BASE);
        attempt(other, otherCategory, true, BASE);
        entityManager.clear();

        assertThat(attemptRepository.findCategoryTypeStats(me.getId()))
                .containsExactly(new CategoryTypeStat("attempt-a", "카테고리 A", 1000, QuestionType.MULTIPLE_CHOICE, 1, 0));
    }

    @Test
    @DisplayName("카테고리 안에서 유형별로 나눈 평평한 행으로 돌려주고 유형을 합친 합계 행은 없다 (R6, D11)")
    void splitsStatsByTypeWithinCategory() {
        Category category = saveCategory("attempt-a", "카테고리 A", 1000);
        Question multipleChoice1 = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식 1");
        Question multipleChoice2 = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식 2");
        saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "풀지 않은 객관식");
        Question shortAnswer = saveQuestion(category, QuestionType.SHORT_ANSWER, "주관식");
        Question essay1 = saveQuestion(category, QuestionType.ESSAY, "서술형 1");
        Question essay2 = saveQuestion(category, QuestionType.ESSAY, "서술형 2");
        Question essay3 = saveQuestion(category, QuestionType.ESSAY, "서술형 3");
        attempt(me, multipleChoice1, true, BASE);
        attempt(me, multipleChoice2, true, BASE);
        attempt(me, shortAnswer, false, BASE);
        attempt(me, essay1, true, BASE);
        attempt(me, essay2, false, BASE);
        attempt(me, essay3, false, BASE);
        entityManager.clear();

        assertThat(attemptRepository.findCategoryTypeStats(me.getId()))
                .containsExactlyInAnyOrder(
                        new CategoryTypeStat("attempt-a", "카테고리 A", 1000, QuestionType.MULTIPLE_CHOICE, 2, 2),
                        new CategoryTypeStat("attempt-a", "카테고리 A", 1000, QuestionType.SHORT_ANSWER, 1, 0),
                        new CategoryTypeStat("attempt-a", "카테고리 A", 1000, QuestionType.ESSAY, 3, 1));
    }

    @Test
    @DisplayName("집계 행은 저장 순서가 아니라 카테고리의 sort_order 순서다")
    void sortsStatsByCategorySortOrder() {
        Question third = saveQuestion(saveCategory("attempt-c", "카테고리 C", 3000), QuestionType.ESSAY, "C");
        Question first = saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "A");
        Question second = saveQuestion(saveCategory("attempt-b", "카테고리 B", 2000), QuestionType.SHORT_ANSWER, "B");
        attempt(me, third, true, BASE);
        attempt(me, first, true, BASE);
        attempt(me, second, true, BASE);
        entityManager.clear();

        assertThat(attemptRepository.findCategoryTypeStats(me.getId()))
                .extracting(CategoryTypeStat::slug)
                .containsExactly("attempt-a", "attempt-b", "attempt-c");
    }

    // 최근 풀이 목록

    @Test
    @DisplayName("최근 풀이 목록은 자기 기록만 돌려준다 (R8)")
    void recentAttemptsAreOnlyMine() {
        Question question =
                saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        long mine = attempt(me, question, true, BASE);
        attempt(other, question, false, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(findRecent(me)).extracting(QuestionAttempt::getId).containsExactly(mine);
    }

    @Test
    @DisplayName("최근 풀이 목록은 attempted_at DESC, id DESC 순서이고 재풀이도 각각 한 줄이다 (R3, R7)")
    void sortsRecentAttemptsByAttemptedAtThenId() {
        Category category = saveCategory("attempt-a", "카테고리 A", 1000);
        Question question = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식");
        Question essay = saveQuestion(category, QuestionType.ESSAY, "서술형");
        long newest = attempt(me, question, true, BASE.plusSeconds(120));
        long oldest = attempt(me, essay, false, BASE);
        long tieSmallerId = attempt(me, question, false, BASE.plusSeconds(60));
        long tieLargerId = attempt(me, essay, true, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(findRecent(me))
                .extracting(QuestionAttempt::getId)
                .containsExactly(newest, tieLargerId, tieSmallerId, oldest);
    }

    @Test
    @DisplayName("최근 풀이 목록은 최대 20건이고 가장 오래된 기록부터 빠진다 (R7, D10)")
    void limitsRecentAttemptsToTwenty() {
        Question question =
                saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i <= RECENT_LIMIT; i++) {
            ids.add(attempt(me, question, i % 2 == 0, BASE.plusSeconds(i)));
        }
        entityManager.clear();

        List<Long> found = findRecent(me).stream().map(QuestionAttempt::getId).toList();

        assertThat(found).hasSize(RECENT_LIMIT).doesNotContain(ids.getFirst());
        assertThat(found.getFirst()).isEqualTo(ids.getLast());
    }

    @Test
    @DisplayName("기록이 없으면 최근 풀이 목록은 빈 목록이다")
    void recentAttemptsAreEmptyWithoutAttempts() {
        assertThat(findRecent(me)).isEmpty();
    }

    @Test
    @DisplayName("최근 풀이 목록은 문제와 카테고리를 함께 불러온다 (N+1 방지)")
    void loadsQuestionAndCategoryWithRecentAttempts() {
        Question question =
                saveQuestion(saveCategory("attempt-a", "카테고리 A", 1000), QuestionType.MULTIPLE_CHOICE, "객관식");
        attempt(me, question, true, BASE);
        entityManager.clear();

        List<QuestionAttempt> found = findRecent(me);

        // 테스트 트랜잭션 안에서는 지연 로딩도 성공하므로, 접근하기 전에 이미 불러왔는지 확인한다
        assertThat(found).hasSize(1);
        assertThat(isLoaded(found.getFirst(), "question")).isTrue();
        assertThat(isLoaded(found.getFirst().getQuestion(), "category")).isTrue();
    }

    private List<QuestionAttempt> findRecent(User user) {
        return attemptRepository.findTop20ByUserIdOrderByAttemptedAtDescIdDesc(user.getId());
    }

    private Category saveCategory(String slug, String name, int sortOrder) {
        return categoryRepository.saveAndFlush(new Category(slug, name, sortOrder));
    }

    /** 생성자가 모범 답안을 받지 않으므로 chk_question_model_answer를 만족하는 주관식·서술형은 SQL로 넣는다. */
    private Question saveQuestion(Category category, QuestionType type, String content) {
        if (type == QuestionType.MULTIPLE_CHOICE) {
            Question question = new Question(category, type, content, null, 10);
            question.addChoice("정답", true, 10);
            question.addChoice("오답", false, 20);
            return questionRepository.saveAndFlush(question);
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
