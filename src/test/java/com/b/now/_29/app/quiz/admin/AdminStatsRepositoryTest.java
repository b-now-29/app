package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.attempt.QuestionAttempt;
import com.b.now._29.app.quiz.attempt.QuestionAttemptRepository;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.ChoiceDraft;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionDraft;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
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
 * 관리자 통계 집계 (docs/features/admin-stats.md R1~R4, D2, D3, D9, D10). 집계 쿼리와, 그 결과를 카테고리 목록과 합치는 Service를 실제
 * DB로 확인한다.
 *
 * <p>구현할 것 ({@code quiz.admin}, D4):
 *
 * <ul>
 *   <li>{@code interface AdminStatsRepository extends Repository<Question, Long>} (Spring Data Repository, JPQL 또는 native)
 *       <ul>
 *         <li>{@code List<AdminCategoryTypeStat> findCategoryTypeStats()} — 삭제 안 된 카테고리의 삭제 안 된 문제에 대해, 회원·문제마다
 *             최근 기록 1건 (attemptedAt desc, id desc)만 세어 (카테고리, 유형)별로 집계한다. 기록이 있는 (카테고리, 유형)만 행이 된다. 행
 *             순서는 묻지 않는다 (유형 순서·카테고리 순서는 Service가 맞춘다)
 *         <li>{@code List<AdminQuestionStatResponse> findQuestionStats(String categorySlug)} — 그 카테고리의 삭제 안 된 문제 전부를
 *             sortOrder asc, id asc 순으로. 기록이 없는 문제도 solvedUsers 0, correctUsers 0으로 나온다 (left join, D10). 카테고리가
 *             없거나 삭제됐으면 빈 목록
 *       </ul>
 *   <li>{@code record AdminCategoryTypeStat(String slug, QuestionType type, long solved, long correct)}
 *   <li>{@code record AdminTypeStatResponse(QuestionType type, long solved, long correct)}
 *   <li>{@code record AdminCategoryStatResponse(String slug, String name, List<AdminTypeStatResponse> types)}
 *   <li>{@code record AdminQuestionStatResponse(long id, QuestionType type, String content, long solvedUsers, long
 *       correctUsers)}
 *   <li>{@code @Service @Transactional(readOnly = true) AdminStatsService} — 생성자 인자는 Repository만 (이 테스트가
 *       {@code @Import}로 띄운다)
 *       <ul>
 *         <li>{@code List<AdminCategoryStatResponse> getCategoryStats()} — 삭제 안 된 모든 카테고리를 sortOrder asc, id asc 순으로.
 *             types는 MULTIPLE_CHOICE → SHORT_ANSWER → ESSAY (enum 순서), 기록이 없으면 빈 목록
 *         <li>{@code List<AdminQuestionStatResponse> getQuestionStats(String categorySlug)} — 없거나 삭제한 카테고리는 {@code
 *             CategoryNotFoundException}
 *       </ul>
 * </ul>
 */
@Tag("integration")
@DataJpaTest
@Import({TestcontainersConfiguration.class, AdminStatsService.class})
class AdminStatsRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");
    private static final String PREFIX = "stats-";

    @Autowired
    AdminStatsRepository adminStatsRepository;

    @Autowired
    AdminStatsService adminStatsService;

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

    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        alice = userRepository.saveAndFlush(new User("stats-alice@example.com", "$2a$10$hash"));
        bob = userRepository.saveAndFlush(new User("stats-bob@example.com", "$2a$10$hash"));
        carol = userRepository.saveAndFlush(new User("stats-carol@example.com", "$2a$10$hash"));
    }

    // 회원·문제마다 최근 기록 1건 (R1)

    @Test
    @DisplayName("한 회원이 같은 문제를 틀림 → 맞음으로 풀면 정답 1, 맞음 → 틀림으로 풀면 정답 0이다 (R1)")
    void countsOnlyLatestAttemptOfEachUser() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question wrongThenRight = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "틀림 → 맞음", 10);
        Question rightThenWrong = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "맞음 → 틀림", 20);
        attempt(alice, wrongThenRight, false, BASE);
        attempt(alice, wrongThenRight, true, BASE.plusSeconds(60));
        attempt(alice, rightThenWrong, true, BASE);
        attempt(alice, rightThenWrong, false, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a"))
                .containsExactly(stat(wrongThenRight, 1, 1), stat(rightThenWrong, 1, 0));
        assertThat(typeStats())
                .containsExactly(new AdminCategoryTypeStat("stats-a", QuestionType.MULTIPLE_CHOICE, 2, 1));
    }

    @Test
    @DisplayName("최근 기록은 id 순서가 아니라 시각 순서로 정하고, 시각이 같으면 id가 큰 기록이다 (R1)")
    void decidesLatestByAttemptedAtThenId() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question byTime = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "시각으로 정함", 10);
        Question byId = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "id로 정함", 20);
        // 먼저 저장해 id가 작지만 시각은 더 나중인 맞음이 최근이다
        attempt(alice, byTime, true, BASE.plusSeconds(120));
        attempt(alice, byTime, false, BASE.plusSeconds(60));
        // 시각이 같으면 나중에 저장한 틀림이 최근이다
        attempt(alice, byId, true, BASE);
        attempt(alice, byId, false, BASE);
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a"))
                .containsExactly(stat(byTime, 1, 1), stat(byId, 1, 0));
    }

    @Test
    @DisplayName("여러 회원의 기록은 회원마다 최근 1건씩 합산하고, 다른 회원의 더 최근 기록이 내 최근 기록을 가리지 않는다 (R1, D9)")
    void sumsLatestAttemptOfEveryUser() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question question = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "여럿이 푼 문제", 10);
        attempt(alice, question, true, BASE);
        attempt(bob, question, false, BASE.plusSeconds(60));
        attempt(carol, question, false, BASE.plusSeconds(10));
        attempt(carol, question, true, BASE.plusSeconds(120));
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a")).containsExactly(stat(question, 3, 2));
        assertThat(typeStats())
                .containsExactly(new AdminCategoryTypeStat("stats-a", QuestionType.MULTIPLE_CHOICE, 3, 2));
    }

    @Test
    @DisplayName("카테고리 통계는 유형별로 나눠 회원·문제 쌍을 센다. 주관식·서술형 자기 채점도 센다 (R2, D5)")
    void splitsCategoryStatsByType() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question multipleChoice1 = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식 1", 10);
        Question multipleChoice2 = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식 2", 20);
        Question shortAnswer = saveQuestion(category, QuestionType.SHORT_ANSWER, "주관식", 30);
        Question essay = saveQuestion(category, QuestionType.ESSAY, "서술형", 40);
        saveQuestion(category, QuestionType.ESSAY, "아무도 안 푼 서술형", 50);
        attempt(alice, multipleChoice1, true, BASE);
        attempt(bob, multipleChoice1, true, BASE);
        attempt(alice, multipleChoice2, false, BASE);
        attempt(alice, shortAnswer, false, BASE);
        attempt(alice, essay, true, BASE);
        attempt(bob, essay, false, BASE);
        attempt(bob, essay, true, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(typeStats())
                .containsExactlyInAnyOrder(
                        new AdminCategoryTypeStat("stats-a", QuestionType.MULTIPLE_CHOICE, 3, 2),
                        new AdminCategoryTypeStat("stats-a", QuestionType.SHORT_ANSWER, 1, 0),
                        new AdminCategoryTypeStat("stats-a", QuestionType.ESSAY, 2, 2));
    }

    // 기록이 0건인 행 (D10: left join에서 최근 1건 조건을 WHERE에 두면 사라진다)

    @Test
    @DisplayName("아무도 안 푼 문제도 문제 통계에 0/0으로 나오고, 옆 문제의 기록에 섞이지 않는다 (R3, D6, D10)")
    void includesQuestionsWithoutAttempts() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question solved = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "푼 문제", 10);
        Question untouched = saveQuestion(category, QuestionType.ESSAY, "안 푼 문제", 20);
        Question untouchedToo = saveQuestion(category, QuestionType.SHORT_ANSWER, "역시 안 푼 문제", 30);
        attempt(alice, solved, false, BASE);
        attempt(alice, solved, true, BASE.plusSeconds(60));
        attempt(bob, solved, false, BASE);
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a"))
                .containsExactly(stat(solved, 2, 1), stat(untouched, 0, 0), stat(untouchedToo, 0, 0));
    }

    @Test
    @DisplayName("기록이 하나도 없는 카테고리의 문제 통계는 모든 문제가 0/0이다 (R3, D10)")
    void allQuestionsAreZeroWithoutAnyAttempt() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question first = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "첫 문제", 10);
        Question second = saveQuestion(category, QuestionType.ESSAY, "둘째 문제", 20);
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a"))
                .containsExactly(stat(first, 0, 0), stat(second, 0, 0));
    }

    @Test
    @DisplayName("기록이 없는 카테고리 (문제가 없거나, 있어도 아무도 안 푼)도 카테고리 통계에 types []로 나온다 (R2, D10)")
    void includesCategoriesWithoutAttempts() {
        Category solved = saveCategory("stats-a", "통계 A", 3000);
        Category unsolved = saveCategory("stats-b", "통계 B", 3001);
        saveCategory("stats-c", "통계 C", 3002);
        Question question = saveQuestion(solved, QuestionType.MULTIPLE_CHOICE, "푼 문제", 10);
        saveQuestion(unsolved, QuestionType.MULTIPLE_CHOICE, "안 푼 문제", 10);
        attempt(alice, question, true, BASE);
        entityManager.clear();

        assertThat(categoryStats())
                .containsExactly(
                        new AdminCategoryStatResponse(
                                "stats-a",
                                "통계 A",
                                List.of(new AdminTypeStatResponse(QuestionType.MULTIPLE_CHOICE, 1, 1))),
                        new AdminCategoryStatResponse("stats-b", "통계 B", List.of()),
                        new AdminCategoryStatResponse("stats-c", "통계 C", List.of()));
    }

    // 삭제한 카테고리·문제 (R4)

    @Test
    @DisplayName("삭제한 문제는 기록이 남아 있어도 문제 통계와 카테고리 통계에서 빠진다 (R4, D1)")
    void excludesDeletedQuestions() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Question kept = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "남은 문제", 10);
        Question deleted = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "삭제한 문제", 20);
        Question deletedEssay = saveQuestion(category, QuestionType.ESSAY, "삭제한 서술형", 30);
        attempt(alice, kept, false, BASE);
        attempt(alice, deleted, true, BASE);
        attempt(bob, deleted, true, BASE);
        attempt(alice, deletedEssay, true, BASE);
        deleted.delete();
        deletedEssay.delete();
        questionRepository.saveAllAndFlush(List.of(deleted, deletedEssay));
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a")).containsExactly(stat(kept, 1, 0));
        assertThat(typeStats())
                .containsExactly(new AdminCategoryTypeStat("stats-a", QuestionType.MULTIPLE_CHOICE, 1, 0));
        assertThat(categoryStats())
                .containsExactly(new AdminCategoryStatResponse(
                        "stats-a", "통계 A", List.of(new AdminTypeStatResponse(QuestionType.MULTIPLE_CHOICE, 1, 0))));
        // 풀이 기록은 지우지 않는다 (R4)
        assertThat(attemptRepository.existsByQuestionId(deleted.getId())).isTrue();
    }

    @Test
    @DisplayName("삭제한 카테고리는 카테고리 통계에서 빠지고, 문제 통계는 빈 목록이며 Service는 404 예외를 던진다 (R4)")
    void excludesDeletedCategories() {
        Category kept = saveCategory("stats-a", "통계 A", 3000);
        Category removed = saveCategory("stats-removed", "삭제한 카테고리", 2999);
        saveQuestion(kept, QuestionType.MULTIPLE_CHOICE, "남은 카테고리의 문제", 10);
        Question orphan = saveQuestion(removed, QuestionType.MULTIPLE_CHOICE, "삭제한 카테고리의 문제", 10);
        attempt(alice, orphan, true, BASE);
        // 카테고리 조건만 확인하려고 문제는 남긴 채 카테고리만 삭제 시각을 넣는다
        entityManager
                .createNativeQuery("UPDATE category SET deleted_at = now() WHERE slug = 'stats-removed'")
                .executeUpdate();
        entityManager.clear();

        assertThat(categoryStats()).containsExactly(new AdminCategoryStatResponse("stats-a", "통계 A", List.of()));
        assertThat(typeStats()).isEmpty();
        assertThat(adminStatsRepository.findQuestionStats("stats-removed")).isEmpty();
        assertThatThrownBy(() -> adminStatsService.getQuestionStats("stats-removed"))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("없는 카테고리의 문제 통계는 Service가 404 예외를 던진다 (API 계약)")
    void unknownCategoryIsNotFound() {
        assertThatThrownBy(() -> adminStatsService.getQuestionStats("stats-nope"))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    // 순서

    @Test
    @DisplayName("카테고리 통계는 sortOrder (같으면 id) 순서이고, types는 MULTIPLE_CHOICE → SHORT_ANSWER → ESSAY 순서다 (R2, API 계약)")
    void ordersCategoriesAndTypes() {
        Category third = saveCategory("stats-c", "통계 C", 3002);
        Category first = saveCategory("stats-a", "통계 A", 3000);
        Category second = saveCategory("stats-b", "통계 B", 3001);
        Category sameOrderLater = saveCategory("stats-d", "통계 D", 3002);
        // 이름순 (ESSAY < MULTIPLE_CHOICE < SHORT_ANSWER)과 enum 순서가 다르도록 서술형부터 만든다
        Question essay = saveQuestion(first, QuestionType.ESSAY, "서술형", 10);
        Question shortAnswer = saveQuestion(first, QuestionType.SHORT_ANSWER, "주관식", 20);
        Question multipleChoice = saveQuestion(first, QuestionType.MULTIPLE_CHOICE, "객관식", 30);
        attempt(alice, essay, true, BASE);
        attempt(alice, shortAnswer, false, BASE);
        attempt(alice, multipleChoice, true, BASE);
        entityManager.clear();

        List<AdminCategoryStatResponse> stats = categoryStats();

        assertThat(stats)
                .extracting(AdminCategoryStatResponse::slug)
                .containsExactly(first.getSlug(), second.getSlug(), third.getSlug(), sameOrderLater.getSlug());
        assertThat(stats.getFirst().types())
                .containsExactly(
                        new AdminTypeStatResponse(QuestionType.MULTIPLE_CHOICE, 1, 1),
                        new AdminTypeStatResponse(QuestionType.SHORT_ANSWER, 1, 0),
                        new AdminTypeStatResponse(QuestionType.ESSAY, 1, 1));
    }

    @Test
    @DisplayName("문제 통계는 관리자 문제 목록과 같은 순서 (sortOrder, 같으면 id)이고 다른 카테고리의 문제는 없다 (R3)")
    void ordersQuestionsLikeAdminQuestionList() {
        Category category = saveCategory("stats-a", "통계 A", 3000);
        Category other = saveCategory("stats-b", "통계 B", 3001);
        Question last = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "표시 순서 30", 30);
        Question firstTie = saveQuestion(category, QuestionType.ESSAY, "표시 순서 10 (먼저 저장)", 10);
        Question secondTie = saveQuestion(category, QuestionType.SHORT_ANSWER, "표시 순서 10 (나중 저장)", 10);
        Question otherCategory = saveQuestion(other, QuestionType.MULTIPLE_CHOICE, "다른 카테고리", 0);
        attempt(alice, otherCategory, true, BASE);
        entityManager.clear();

        assertThat(adminStatsRepository.findQuestionStats("stats-a"))
                .containsExactly(stat(firstTie, 0, 0), stat(secondTie, 0, 0), stat(last, 0, 0));
        assertThat(adminStatsService.getQuestionStats("stats-a"))
                .containsExactly(stat(firstTie, 0, 0), stat(secondTie, 0, 0), stat(last, 0, 0));
    }

    @Test
    @DisplayName("시드 카테고리도 카테고리 통계에 들어 있다 (R2)")
    void includesSeedCategories() {
        assertThat(adminStatsService.getCategoryStats())
                .extracting(AdminCategoryStatResponse::slug)
                .contains("java");
    }

    private List<AdminCategoryTypeStat> typeStats() {
        return adminStatsRepository.findCategoryTypeStats().stream()
                .filter(row -> row.slug().startsWith(PREFIX))
                .toList();
    }

    private List<AdminCategoryStatResponse> categoryStats() {
        return adminStatsService.getCategoryStats().stream()
                .filter(row -> row.slug().startsWith(PREFIX))
                .toList();
    }

    private static AdminQuestionStatResponse stat(Question question, long solvedUsers, long correctUsers) {
        return new AdminQuestionStatResponse(
                question.getId(), question.getType(), question.getContent(), solvedUsers, correctUsers);
    }

    private Category saveCategory(String slug, String name, int sortOrder) {
        return categoryRepository.saveAndFlush(new Category(slug, name, sortOrder));
    }

    private Question saveQuestion(Category category, QuestionType type, String content, int sortOrder) {
        QuestionDraft draft = type == QuestionType.MULTIPLE_CHOICE
                ? new QuestionDraft(
                        type,
                        content,
                        null,
                        null,
                        sortOrder,
                        List.of(new ChoiceDraft("정답", true), new ChoiceDraft("오답", false)))
                : new QuestionDraft(type, content, null, "모범 답안", sortOrder, List.of());
        return questionRepository.saveAndFlush(Question.create(category, draft));
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
}
