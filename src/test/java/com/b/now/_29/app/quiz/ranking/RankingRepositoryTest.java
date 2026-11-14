package com.b.now._29.app.quiz.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.attempt.QuestionAttempt;
import com.b.now._29.app.quiz.attempt.QuestionAttemptRepository;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import com.b.now._29.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 구현할 API (D3: 네이티브 SQL 윈도 함수. 같은 (회원, 문제)의 직전 기록과 비교한 점수 변화로 점수와 도달 시각을 구한다).
 *
 * <ul>
 *   <li>{@code interface RankingRepository extends Repository<..>}
 *   <li>{@code record RankingRow(long rank, String nickname, int tag, long score)}
 *   <li>{@code List<RankingRow> findTopRankings(int limit)}: 전체 랭킹 상위 limit명, 등수 순
 *   <li>{@code Optional<RankingRow> findUserRanking(long userId)}: 전체 랭킹에서 그 회원의 줄. 랭킹에 없으면 (점수 0, 관리자) empty
 *   <li>{@code List<RankingRow> findTopRankingsInCategory(String slug, int limit)}
 *   <li>{@code Optional<RankingRow> findUserRankingInCategory(String slug, long userId)}
 * </ul>
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class RankingRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");
    private static final int LIMIT = 50;

    @Autowired
    RankingRepository rankingRepository;

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

    private int emailSequence;

    // 기본

    @Test
    @DisplayName("기록이 없으면 랭킹은 빈 목록이고 회원의 줄도 없다")
    void emptyWithoutAttempts() {
        User user = saveUser("랭커가");
        saveQuestion(saveCategory("rank-a", 1000), QuestionType.MULTIPLE_CHOICE, "문제");
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT)).isEmpty();
        assertThat(rankingRepository.findUserRanking(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("줄은 등수, 닉네임, 번호, 점수를 담는다 (R6)")
    void mapsRow() {
        User user = userRepository.saveAndFlush(new User("rank-map@example.com", "$2a$10$hash", new Nickname("철수"), 3));
        Category category = saveCategory("rank-a", 1000);
        attempt(user, saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "문제 1"), true, BASE);
        attempt(user, saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "문제 2"), true, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT)).containsExactly(new RankingRow(1L, "철수", 3, 2L));
        assertThat(rankingRepository.findUserRanking(user.getId())).contains(new RankingRow(1L, "철수", 3, 2L));
    }

    @Test
    @DisplayName("점수가 높은 순이고 등수는 1부터 하나씩 커진다 (R4)")
    void ordersByScoreDescending() {
        User low = saveUser("랭커가");
        User high = saveUser("랭커나");
        User middle = saveUser("랭커다");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 3);
        correct(low, questions, 1, BASE);
        correct(high, questions, 3, BASE);
        correct(middle, questions, 2, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple(1L, "랭커나", 3L), tuple(2L, "랭커다", 2L), tuple(3L, "랭커가", 1L));
    }

    // 점수 (R1)

    @Test
    @DisplayName("객관식, 주관식, 서술형 모두 맞힌 문제로 센다 (R1, D1)")
    void countsAllQuestionTypes() {
        User user = saveUser("랭커가");
        Category category = saveCategory("rank-a", 1000);
        attempt(user, saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "객관식"), true, BASE);
        attempt(user, saveQuestion(category, QuestionType.SHORT_ANSWER, "주관식"), true, BASE);
        attempt(user, saveQuestion(category, QuestionType.ESSAY, "서술형"), true, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::score)
                .containsExactly(3L);
    }

    @Test
    @DisplayName("문제마다 가장 최근 기록만 보고, 같은 문제를 여러 번 맞혀도 한 문제로 센다 (R1)")
    void countsLatestAttemptPerQuestionOnce() {
        User user = saveUser("랭커가");
        Category category = saveCategory("rank-a", 1000);
        Question fixed = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "틀린 뒤 맞힘");
        Question lost = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "맞힌 뒤 틀림");
        Question repeated = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "여러 번 맞힘");
        attempt(user, fixed, false, BASE);
        attempt(user, fixed, true, BASE.plusSeconds(10));
        attempt(user, lost, true, BASE);
        attempt(user, lost, false, BASE.plusSeconds(10));
        attempt(user, repeated, true, BASE);
        attempt(user, repeated, true, BASE.plusSeconds(10));
        attempt(user, repeated, true, BASE.plusSeconds(20));
        entityManager.clear();

        assertThat(rankingRepository.findUserRanking(user.getId()))
                .get()
                .extracting(RankingRow::score)
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("최근 기록은 저장 순서(id)가 아니라 시각으로 정한다 (R1)")
    void latestIsDecidedByAttemptedAtNotId() {
        User user = saveUser("랭커가");
        Category category = saveCategory("rank-a", 1000);
        Question correctLater = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "나중 시각에 맞힘");
        Question wrongLater = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "나중 시각에 틀림");
        // 먼저 저장해 id가 작지만 시각은 더 나중인 기록이 최근이다
        attempt(user, correctLater, true, BASE.plusSeconds(120));
        attempt(user, correctLater, false, BASE.plusSeconds(60));
        attempt(user, wrongLater, false, BASE.plusSeconds(120));
        attempt(user, wrongLater, true, BASE.plusSeconds(60));
        entityManager.clear();

        assertThat(rankingRepository.findUserRanking(user.getId()))
                .get()
                .extracting(RankingRow::score)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("한 문제의 기록 시각이 같으면 id가 큰 기록이 최근이다 (R1)")
    void breaksTieOfAttemptedAtById() {
        User correctLast = saveUser("랭커가");
        User wrongLast = saveUser("랭커나");
        Question question = saveQuestion(saveCategory("rank-a", 1000), QuestionType.MULTIPLE_CHOICE, "문제");
        attempt(correctLast, question, false, BASE);
        attempt(correctLast, question, true, BASE);
        attempt(wrongLast, question, true, BASE);
        attempt(wrongLast, question, false, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("랭커가", 1L));
        assertThat(rankingRepository.findUserRanking(wrongLast.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 회원의 기록은 내 점수 변화 계산에 섞이지 않는다 (R1, D3)")
    void historyIsPerUserAndQuestion() {
        User first = saveUser("랭커가");
        User second = saveUser("랭커나");
        Question question = saveQuestion(saveCategory("rank-a", 1000), QuestionType.MULTIPLE_CHOICE, "같이 푼 문제");
        attempt(first, question, true, BASE);
        // 같은 문제의 다른 회원 기록이 직전 기록으로 잡히면 −1이 되거나 +1이 빠진다
        attempt(second, question, false, BASE.plusSeconds(10));
        attempt(second, question, true, BASE.plusSeconds(20));
        attempt(first, question, true, BASE.plusSeconds(30));
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("랭커가", 1L), tuple("랭커나", 1L));
    }

    // 동점과 도달 시각 (R4, D2)

    @Test
    @DisplayName("점수가 같으면 지금 점수에 먼저 도달한 회원이 위다. 회원 id보다 먼저 본다 (R4, D2)")
    void tieBrokenByReachedAt() {
        User late = saveUser("늦게도달");
        User early = saveUser("먼저도달");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 2);
        attempt(late, questions.get(0), true, BASE);
        attempt(late, questions.get(1), true, BASE.plusSeconds(300));
        attempt(early, questions.get(0), true, BASE.plusSeconds(100));
        attempt(early, questions.get(1), true, BASE.plusSeconds(200));
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple(1L, "먼저도달", 2L), tuple(2L, "늦게도달", 2L));
    }

    @Test
    @DisplayName("점수를 바꾸지 않은 기록 (맞음→맞음, 틀림→틀림, 처음 틀림)은 도달 시각을 바꾸지 않는다 (D2)")
    void unchangedAttemptsDoNotMoveReachedAt() {
        User repeater = saveUser("반복풀이");
        User other = saveUser("한번풀이");
        Category category = saveCategory("rank-a", 1000);
        Question solved = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "맞힌 문제");
        Question neverSolved = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "계속 틀린 문제");
        Question otherQuestion = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "다른 문제");
        attempt(repeater, solved, true, BASE);
        attempt(repeater, solved, true, BASE.plusSeconds(500));
        attempt(repeater, neverSolved, false, BASE.plusSeconds(600));
        attempt(repeater, neverSolved, false, BASE.plusSeconds(700));
        attempt(other, otherQuestion, true, BASE.plusSeconds(100));
        entityManager.clear();

        // 마지막 기록 시각으로 보면 반복풀이(700)가 아래지만, 점수를 바꾼 마지막 기록은 0초다
        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("반복풀이", 1L), tuple("한번풀이", 1L));
    }

    @Test
    @DisplayName("맞음→틀림은 점수를 1 내리고, 도달 시각은 그 틀린 기록의 시각이 된다 (R4, D2)")
    void scoreDropMovesReachedAt() {
        User dropped = saveUser("떨어짐");
        User middle = saveUser("중간");
        User last = saveUser("마지막");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 3);
        attempt(dropped, questions.get(0), true, BASE.plusSeconds(10));
        attempt(dropped, questions.get(1), true, BASE.plusSeconds(20));
        attempt(dropped, questions.get(1), false, BASE.plusSeconds(600));
        attempt(middle, questions.get(2), true, BASE.plusSeconds(400));
        attempt(last, questions.get(0), true, BASE.plusSeconds(500));
        entityManager.clear();

        // "점수에 들어간 기록 중 가장 늦은 시각"(10초)이면 떨어짐이 1위가 된다 (D2의 버린 대안)
        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("중간", 1L), tuple("마지막", 1L), tuple("떨어짐", 1L));
    }

    @Test
    @DisplayName("떨어진 뒤 다시 맞히면 도달 시각은 다시 맞힌 시각이다 (D2)")
    void regainMovesReachedAt() {
        User regained = saveUser("되찾음");
        User other = saveUser("한번풀이");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 2);
        attempt(regained, questions.get(0), true, BASE);
        attempt(regained, questions.get(0), false, BASE.plusSeconds(100));
        attempt(regained, questions.get(0), true, BASE.plusSeconds(300));
        attempt(other, questions.get(1), true, BASE.plusSeconds(200));
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("한번풀이", 1L), tuple("되찾음", 1L));
    }

    @Test
    @DisplayName("점수와 도달 시각이 모두 같으면 회원 id가 작은 쪽이 위다. 같은 등수는 없다 (R4)")
    void tieBrokenByUserId() {
        User smallerId = saveUser("먼저가입");
        User largerId = saveUser("나중가입");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 2);
        // 기록은 id가 큰 회원 것을 먼저 저장해 기록 id 순서와 회원 id 순서를 엇갈리게 한다
        attempt(largerId, questions.get(0), true, BASE);
        attempt(smallerId, questions.get(1), true, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname)
                .containsExactly(tuple(1L, "먼저가입"), tuple(2L, "나중가입"));
    }

    // 삭제된 문제·카테고리 (R2)

    @Test
    @DisplayName("삭제된 문제와 삭제된 카테고리의 문제는 점수에 넣지 않는다 (R2)")
    void excludesDeletedQuestionAndCategoryFromScore() {
        User user = saveUser("랭커가");
        User onlyDeleted = saveUser("삭제만");
        Category alive = saveCategory("rank-a", 1000);
        Category deletedCategory = saveCategory("rank-b", 2000);
        Question kept = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "남는 문제");
        Question deletedQuestion = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "삭제한 문제");
        Question inDeletedCategory = saveQuestion(deletedCategory, QuestionType.ESSAY, "삭제한 카테고리 문제");
        attempt(user, kept, true, BASE);
        attempt(user, deletedQuestion, true, BASE);
        attempt(user, inDeletedCategory, true, BASE);
        attempt(onlyDeleted, deletedQuestion, true, BASE);
        attempt(onlyDeleted, inDeletedCategory, true, BASE);
        deleteQuestion(deletedQuestion);
        deleteCategory(deletedCategory);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("랭커가", 1L));
        assertThat(rankingRepository.findUserRanking(onlyDeleted.getId())).isEmpty();
    }

    @Test
    @DisplayName("삭제된 문제의 기록은 도달 시각 계산에서도 뺀다 (R2)")
    void excludesDeletedQuestionFromReachedAt() {
        User withDeleted = saveUser("삭제포함");
        User other = saveUser("비교대상");
        Category category = saveCategory("rank-a", 1000);
        Question kept = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "남는 문제");
        Question deleted = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "삭제한 문제");
        Question otherQuestion = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "다른 문제");
        attempt(withDeleted, kept, true, BASE);
        // 삭제한 문제를 넣으면 맞음→틀림의 −1로 도달 시각이 500초가 된다
        attempt(withDeleted, deleted, true, BASE.plusSeconds(400));
        attempt(withDeleted, deleted, false, BASE.plusSeconds(500));
        attempt(other, otherQuestion, true, BASE.plusSeconds(100));
        deleteQuestion(deleted);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("삭제포함", 1L), tuple("비교대상", 1L));
    }

    @Test
    @DisplayName("삭제된 카테고리 문제의 기록은 도달 시각 계산에서도 뺀다 (R2)")
    void excludesDeletedCategoryFromReachedAt() {
        User withDeleted = saveUser("삭제포함");
        User other = saveUser("비교대상");
        Category alive = saveCategory("rank-a", 1000);
        Category deletedCategory = saveCategory("rank-b", 2000);
        Question kept = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "남는 문제");
        Question otherQuestion = saveQuestion(alive, QuestionType.MULTIPLE_CHOICE, "다른 문제");
        Question inDeletedCategory = saveQuestion(deletedCategory, QuestionType.MULTIPLE_CHOICE, "삭제한 카테고리 문제");
        attempt(withDeleted, kept, true, BASE);
        attempt(withDeleted, inDeletedCategory, true, BASE.plusSeconds(400));
        attempt(withDeleted, inDeletedCategory, false, BASE.plusSeconds(500));
        attempt(other, otherQuestion, true, BASE.plusSeconds(100));
        deleteCategory(deletedCategory);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("삭제포함", 1L), tuple("비교대상", 1L));
    }

    // 랭킹에서 빼는 회원 (R3)

    @Test
    @DisplayName("관리자는 점수가 높아도 랭킹에 넣지 않고 등수도 차지하지 않는다 (R3, D4)")
    void excludesAdmins() {
        User admin =
                userRepository.saveAndFlush(new User(nextEmail(), "$2a$10$hash", Role.ADMIN, new Nickname("관리자"), 1));
        User user = saveUser("회원");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 3);
        correct(admin, questions, 3, BASE);
        correct(user, questions, 1, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname)
                .containsExactly(tuple(1L, "회원"));
        assertThat(rankingRepository.findUserRanking(admin.getId())).isEmpty();
        assertThat(rankingRepository.findUserRanking(user.getId()))
                .get()
                .extracting(RankingRow::rank)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("관리자 여부는 조회 시점의 role로 정한다. 기록을 남긴 뒤 관리자가 되면 빠진다 (R3)")
    void excludesUserPromotedToAdminAfterAttempts() {
        User promoted = saveUser("승격회원");
        User actor = saveUser("지정한사람");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 2);
        correct(promoted, questions, 2, BASE);
        correct(actor, questions, 1, BASE);
        User managed = userRepository.findById(promoted.getId()).orElseThrow();
        managed.changeRole(Role.ADMIN, actor.getId());
        userRepository.saveAndFlush(managed);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname)
                .containsExactly("지정한사람");
    }

    @Test
    @DisplayName("점수가 0인 회원 (모두 틀림, 맞힌 뒤 틀림)은 랭킹에 넣지 않는다 (R3)")
    void excludesZeroScore() {
        User allWrong = saveUser("모두틀림");
        User droppedToZero = saveUser("영점");
        User scorer = saveUser("득점");
        Question question = saveQuestion(saveCategory("rank-a", 1000), QuestionType.MULTIPLE_CHOICE, "문제");
        attempt(allWrong, question, false, BASE);
        attempt(droppedToZero, question, true, BASE);
        attempt(droppedToZero, question, false, BASE.plusSeconds(10));
        attempt(scorer, question, true, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname)
                .containsExactly(tuple(1L, "득점"));
        assertThat(rankingRepository.findUserRanking(allWrong.getId())).isEmpty();
        assertThat(rankingRepository.findUserRanking(droppedToZero.getId())).isEmpty();
    }

    // 상위 N명과 내 순위 (R6, R7)

    @Test
    @DisplayName("목록은 상위 limit명까지만 돌려준다 (R6, D5)")
    void limitsTopRankings() {
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 3);
        correct(saveUser("일등"), questions, 3, BASE);
        correct(saveUser("삼등"), questions, 1, BASE);
        correct(saveUser("이등"), questions, 2, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(2))
                .extracting(RankingRow::rank, RankingRow::nickname)
                .containsExactly(tuple(1L, "일등"), tuple(2L, "이등"));
    }

    @Test
    @DisplayName("목록 밖의 회원도 전체 순위 기준의 자기 등수와 점수를 받는다 (R7)")
    void userRankingOutsideTopLimit() {
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 3);
        correct(saveUser("일등"), questions, 3, BASE);
        correct(saveUser("이등"), questions, 2, BASE);
        User third = saveUser("삼등");
        correct(third, questions, 1, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(2)).hasSize(2);
        assertThat(rankingRepository.findUserRanking(third.getId())).contains(new RankingRow(3L, "삼등", 1, 1L));
    }

    @Test
    @DisplayName("회원의 줄은 동점 규칙까지 적용한 목록의 줄과 같다 (R4, R7)")
    void userRankingMatchesListWithTieBreaks() {
        User first = saveUser("가회원");
        User second = saveUser("나회원");
        User third = saveUser("다회원");
        List<Question> questions = saveQuestions(saveCategory("rank-a", 1000), 2);
        // 셋 다 1점. 도달 시각: 다(0초) < 가 = 나(100초), 가와 나는 회원 id로 가른다
        attempt(third, questions.get(0), true, BASE);
        attempt(second, questions.get(0), true, BASE.plusSeconds(100));
        attempt(first, questions.get(1), true, BASE.plusSeconds(100));
        entityManager.clear();

        List<RankingRow> top = rankingRepository.findTopRankings(LIMIT);
        assertThat(top)
                .extracting(RankingRow::rank, RankingRow::nickname)
                .containsExactly(tuple(1L, "다회원"), tuple(2L, "가회원"), tuple(3L, "나회원"));
        assertThat(rankingRepository.findUserRanking(third.getId())).contains(top.get(0));
        assertThat(rankingRepository.findUserRanking(first.getId())).contains(top.get(1));
        assertThat(rankingRepository.findUserRanking(second.getId())).contains(top.get(2));
    }

    // 카테고리별 (R5)

    @Test
    @DisplayName("카테고리 랭킹은 그 카테고리 문제만으로 점수를 센다 (R5)")
    void categoryRankingCountsOnlyThatCategory() {
        User javaFan = saveUser("자바팬");
        User springFan = saveUser("스프링팬");
        List<Question> java = saveQuestions(saveCategory("rank-java", 1000), 3);
        List<Question> spring = saveQuestions(saveCategory("rank-spring", 2000), 3);
        correct(javaFan, java, 2, BASE);
        correct(javaFan, spring, 1, BASE);
        correct(springFan, java, 1, BASE);
        correct(springFan, spring, 3, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankings(LIMIT))
                .extracting(RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple("스프링팬", 4L), tuple("자바팬", 3L));
        assertThat(rankingRepository.findTopRankingsInCategory("rank-java", LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple(1L, "자바팬", 2L), tuple(2L, "스프링팬", 1L));
        assertThat(rankingRepository.findTopRankingsInCategory("rank-spring", LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple(1L, "스프링팬", 3L), tuple(2L, "자바팬", 1L));
    }

    @Test
    @DisplayName("카테고리 랭킹의 도달 시각은 그 카테고리 기록만으로 구한다 (R5, D2)")
    void categoryReachedAtUsesOnlyThatCategory() {
        User mixed = saveUser("여러카테고리");
        User single = saveUser("한카테고리");
        Category javaCategory = saveCategory("rank-java", 1000);
        Question java1 = saveQuestion(javaCategory, QuestionType.MULTIPLE_CHOICE, "자바 1");
        Question java2 = saveQuestion(javaCategory, QuestionType.MULTIPLE_CHOICE, "자바 2");
        Question spring = saveQuestion(saveCategory("rank-spring", 2000), QuestionType.MULTIPLE_CHOICE, "스프링");
        attempt(mixed, java1, true, BASE);
        // 다른 카테고리 기록이 섞이면 도달 시각이 500초가 되어 아래로 간다
        attempt(mixed, spring, true, BASE.plusSeconds(500));
        attempt(single, java2, true, BASE.plusSeconds(100));
        entityManager.clear();

        assertThat(rankingRepository.findTopRankingsInCategory("rank-java", LIMIT))
                .extracting(RankingRow::nickname)
                .containsExactly("여러카테고리", "한카테고리");
    }

    @Test
    @DisplayName("카테고리 랭킹도 삭제된 문제, 관리자, 점수 0을 뺀다 (R2, R3, R5)")
    void categoryRankingAppliesSameExclusions() {
        User admin =
                userRepository.saveAndFlush(new User(nextEmail(), "$2a$10$hash", Role.ADMIN, new Nickname("관리자"), 1));
        User zero = saveUser("영점");
        User onlyDeleted = saveUser("삭제만");
        User scorer = saveUser("득점");
        Category category = saveCategory("rank-java", 1000);
        Question kept = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "남는 문제");
        Question deleted = saveQuestion(category, QuestionType.MULTIPLE_CHOICE, "삭제한 문제");
        attempt(admin, kept, true, BASE);
        attempt(zero, kept, false, BASE);
        attempt(onlyDeleted, deleted, true, BASE);
        attempt(scorer, kept, true, BASE);
        deleteQuestion(deleted);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankingsInCategory("rank-java", LIMIT))
                .extracting(RankingRow::rank, RankingRow::nickname, RankingRow::score)
                .containsExactly(tuple(1L, "득점", 1L));
        assertThat(rankingRepository.findUserRankingInCategory("rank-java", admin.getId()))
                .isEmpty();
        assertThat(rankingRepository.findUserRankingInCategory("rank-java", zero.getId()))
                .isEmpty();
        assertThat(rankingRepository.findUserRankingInCategory("rank-java", onlyDeleted.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("카테고리 랭킹도 상위 limit명이고, 그 밖의 회원은 카테고리 안의 자기 등수를 받는다 (R5, R7)")
    void categoryUserRankingOutsideTopLimit() {
        List<Question> java = saveQuestions(saveCategory("rank-java", 1000), 3);
        List<Question> spring = saveQuestions(saveCategory("rank-spring", 2000), 3);
        correct(saveUser("일등"), java, 3, BASE);
        correct(saveUser("이등"), java, 2, BASE);
        User third = saveUser("삼등");
        correct(third, java, 1, BASE);
        // 전체로는 1등이지만 자바에서는 3등이다
        correct(third, spring, 3, BASE);
        User none = saveUser("자바안함");
        correct(none, spring, 1, BASE);
        entityManager.clear();

        assertThat(rankingRepository.findTopRankingsInCategory("rank-java", 2))
                .extracting(RankingRow::nickname)
                .containsExactly("일등", "이등");
        assertThat(rankingRepository.findUserRankingInCategory("rank-java", third.getId()))
                .contains(new RankingRow(3L, "삼등", 1, 1L));
        assertThat(rankingRepository.findUserRankingInCategory("rank-java", none.getId()))
                .isEmpty();
        assertThat(rankingRepository.findUserRanking(third.getId()))
                .get()
                .extracting(RankingRow::rank, RankingRow::score)
                .containsExactly(1L, 4L);
    }

    // 준비

    private String nextEmail() {
        emailSequence++;
        return "ranker" + emailSequence + "@example.com";
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(new User(nextEmail(), "$2a$10$hash", new Nickname(nickname), 1));
    }

    private Category saveCategory(String slug, int sortOrder) {
        return categoryRepository.saveAndFlush(new Category(slug, "카테고리 " + slug, sortOrder));
    }

    private List<Question> saveQuestions(Category category, int count) {
        List<Question> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add(saveQuestion(category, QuestionType.MULTIPLE_CHOICE, category.getSlug() + " 문제 " + i));
        }
        return questions;
    }

    /** 앞에서부터 count개 문제를 같은 시각에 맞힌다. */
    private void correct(User user, List<Question> questions, int count, Instant at) {
        for (int i = 0; i < count; i++) {
            attempt(user, questions.get(i), true, at);
        }
    }

    private void deleteQuestion(Question question) {
        Question managed = questionRepository.findById(question.getId()).orElseThrow();
        managed.delete();
        questionRepository.saveAndFlush(managed);
    }

    /** 문제 수 검증은 삭제 대상이 아니므로 남은 문제 0개로 지운다. 문제 행은 그대로 둔다. */
    private void deleteCategory(Category category) {
        Category managed = categoryRepository.findById(category.getId()).orElseThrow();
        managed.delete(0);
        categoryRepository.saveAndFlush(managed);
    }

    private Question saveMultipleChoice(Category category, String content) {
        Question question = new Question(category, QuestionType.MULTIPLE_CHOICE, content, null, 10);
        question.addChoice("정답", true, 10);
        question.addChoice("오답", false, 20);
        return questionRepository.saveAndFlush(question);
    }

    /** 생성자가 모범 답안을 받지 않으므로 chk_question_model_answer를 만족하는 주관식·서술형은 SQL로 넣는다. */
    private Question saveQuestion(Category category, QuestionType type, String content) {
        if (type == QuestionType.MULTIPLE_CHOICE) {
            return saveMultipleChoice(category, content);
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
}
