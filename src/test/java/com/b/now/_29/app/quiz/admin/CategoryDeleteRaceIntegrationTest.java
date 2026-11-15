package com.b.now._29.app.quiz.admin;

import static com.b.now._29.app.support.TransactionRace.TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.CategoryNotEmptyException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.TransactionRace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 카테고리 삭제와 그 카테고리에 문제를 넣는 요청이 동시에 실행될 때, 삭제된 카테고리 안에 활성 문제가 남지 않는지 실제 트랜잭션과 DB로
 * 확인한다 (#34).
 *
 * <p>먼저 시작한 쪽은 {@link TransactionTemplate} 안에서 작업을 마친 뒤 커밋하지 않고 기다린다. 그동안 다른 쪽의 요청이 끝나지 않아야
 * (블록되어야) 하고, 먼저 시작한 쪽이 커밋한 뒤에는 커밋된 결과를 기준으로 404 또는 409가 되어야 한다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@Import(TestcontainersConfiguration.class)
class CategoryDeleteRaceIntegrationTest {

    @Autowired
    AdminCategoryService adminCategoryService;

    @Autowired
    AdminQuestionService adminQuestionService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> slugs = new ArrayList<>();

    private TransactionRace race;
    private String slug;

    @BeforeEach
    void setUp() {
        race = new TransactionRace(transactionTemplate);
        slug = newCategory();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        race.close();
        for (String created : slugs) {
            jdbc.update("DELETE FROM question WHERE category_id IN (SELECT id FROM category WHERE slug = ?)", created);
            jdbc.update("DELETE FROM category WHERE slug = ?", created);
        }
    }

    @Test
    @DisplayName("삭제가 먼저 커밋 전이면 문제 등록은 기다렸다가 CategoryNotFoundException이고, 삭제된 카테고리에 활성 문제가 없다")
    void createWaitsForDeleteThenNotFound() throws Exception {
        Future<?> deleting = race.holdFirst(() -> adminCategoryService.delete(slug));

        Future<AdminQuestionResponse> creating = race.submit(() -> adminQuestionService.create(shortAnswer(slug)));

        race.assertBlocked(creating);
        race.releaseFirst();
        deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> creating.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CategoryNotFoundException.class);
        assertThat(isDeleted(slug)).isTrue();
        assertThat(activeQuestionCount(slug)).isZero();
    }

    @Test
    @DisplayName("삭제가 먼저 커밋 전이면 그 카테고리로 옮기는 문제 수정은 기다렸다가 CategoryNotFoundException이고, 문제는 원래 카테고리에 남는다")
    void updateWaitsForDeleteThenNotFound() throws Exception {
        String other = newCategory();
        long questionId = adminQuestionService.create(shortAnswer(other)).id();
        Future<?> deleting = race.holdFirst(() -> adminCategoryService.delete(slug));

        Future<AdminQuestionResponse> moving =
                race.submit(() -> adminQuestionService.update(questionId, shortAnswer(slug)));

        race.assertBlocked(moving);
        race.releaseFirst();
        deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> moving.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CategoryNotFoundException.class);
        assertThat(isDeleted(slug)).isTrue();
        assertThat(activeQuestionCount(slug)).isZero();
        assertThat(activeQuestionCount(other)).isEqualTo(1);
    }

    @Test
    @DisplayName("문제 등록이 먼저 커밋 전이면 삭제는 기다렸다가 CategoryNotEmptyException이고, 카테고리와 문제가 남는다")
    void deleteWaitsForCreateThenNotEmpty() throws Exception {
        Future<?> creating = race.holdFirst(() -> adminQuestionService.create(shortAnswer(slug)));

        Future<?> deleting = race.submit(() -> adminCategoryService.delete(slug));

        race.assertBlocked(deleting);
        race.releaseFirst();
        creating.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CategoryNotEmptyException.class);
        assertThat(isDeleted(slug)).isFalse();
        assertThat(activeQuestionCount(slug)).isEqualTo(1);
    }

    private String newCategory() {
        String created = "race-" + UUID.randomUUID().toString().substring(0, 8);
        slugs.add(created);
        adminCategoryService.create(new CategoryCreateRequest(created, "경합 테스트", null, 5000));
        return created;
    }

    private static QuestionRequest shortAnswer(String categorySlug) {
        return new QuestionRequest(categorySlug, QuestionType.SHORT_ANSWER, "주관식", null, "모범 답안", 0, List.of());
    }

    private boolean isDeleted(String categorySlug) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM category WHERE slug = ?", Boolean.class, categorySlug));
    }

    private long activeQuestionCount(String categorySlug) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM question q JOIN category c ON c.id = q.category_id
                WHERE c.slug = ? AND q.deleted_at IS NULL
                """, Long.class, categorySlug);
        return count == null ? 0 : count;
    }
}
