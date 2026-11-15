package com.b.now._29.app.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.category.CategoryNotEmptyException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.support.TestJwts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    /** 뒤에 시작한 요청이 이 시간 안에 끝나면 블록되지 않은 것으로 본다. */
    private static final long BLOCKED_MILLIS = 500;

    /** 테스트가 멈추지 않도록 모든 대기에 두는 상한. */
    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    AdminCategoryService adminCategoryService;

    @Autowired
    AdminQuestionService adminQuestionService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> slugs = new ArrayList<>();

    private String slug;

    @BeforeEach
    void setUp() {
        slug = newCategory();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        release.countDown();
        executor.shutdown();
        if (!executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        for (String created : slugs) {
            jdbc.update("DELETE FROM question WHERE category_id IN (SELECT id FROM category WHERE slug = ?)", created);
            jdbc.update("DELETE FROM category WHERE slug = ?", created);
        }
    }

    @Test
    @DisplayName("삭제가 먼저 커밋 전이면 문제 등록은 기다렸다가 CategoryNotFoundException이고, 삭제된 카테고리에 활성 문제가 없다")
    void createWaitsForDeleteThenNotFound() throws Exception {
        Future<?> deleting = startFirst(() -> adminCategoryService.delete(slug));

        Future<AdminQuestionResponse> creating = executor.submit(() -> adminQuestionService.create(shortAnswer(slug)));

        assertBlocked(creating);
        release.countDown();
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
        Future<?> deleting = startFirst(() -> adminCategoryService.delete(slug));

        Future<AdminQuestionResponse> moving =
                executor.submit(() -> adminQuestionService.update(questionId, shortAnswer(slug)));

        assertBlocked(moving);
        release.countDown();
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
        Future<?> creating = startFirst(() -> adminQuestionService.create(shortAnswer(slug)));

        Future<?> deleting = executor.submit(() -> adminCategoryService.delete(slug));

        assertBlocked(deleting);
        release.countDown();
        creating.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CategoryNotEmptyException.class);
        assertThat(isDeleted(slug)).isFalse();
        assertThat(activeQuestionCount(slug)).isEqualTo(1);
    }

    /** 한 트랜잭션 안에서 작업을 마친 뒤, 커밋하지 않고 {@code release}까지 기다린다. 작업을 마칠 때까지 기다렸다가 돌아온다. */
    private Future<?> startFirst(Runnable work) throws Exception {
        Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            work.run();
            firstDone.countDown();
            awaitRelease();
        }));
        if (!firstDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new AssertionError("먼저 시작한 트랜잭션이 작업을 마치지 못했다");
        }
        return first;
    }

    private void awaitRelease() {
        try {
            release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertBlocked(Future<?> later) {
        assertThatThrownBy(() -> later.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS))
                .as("먼저 시작한 트랜잭션이 커밋하기 전에는 끝나지 않아야 한다")
                .isInstanceOf(TimeoutException.class);
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
