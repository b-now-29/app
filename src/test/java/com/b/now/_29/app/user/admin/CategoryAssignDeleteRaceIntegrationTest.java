package com.b.now._29.app.user.admin;

import static com.b.now._29.app.support.TransactionRace.TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.quiz.admin.AdminCategoryService;
import com.b.now._29.app.quiz.category.CategoryHasSoleAdminException;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.TransactionRace;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserService;
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
 * 카테고리 삭제와 그 카테고리를 담당으로 지정하는 역할 변경이 동시에 실행돼도, 삭제된 카테고리만 담당하는 관리자가 남지 않는지 실제 트랜잭션과 DB로
 * 확인한다 (role-owner-category-admin R9, R10, API 계약: 담당 카테고리는 {@code findWithShareLockBySlugAndDeletedAtIsNull}로 잠근다).
 *
 * <p>먼저 시작한 쪽은 작업을 마친 뒤 커밋하지 않고 기다린다. 그동안 다른 쪽은 블록되어야 하고, 먼저 시작한 쪽이 커밋한 뒤에는 커밋된 결과를 기준으로 404
 * 또는 409가 되어야 한다 (#34와 같은 방식).
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@Import(TestcontainersConfiguration.class)
class CategoryAssignDeleteRaceIntegrationTest {

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    AdminCategoryService adminCategoryService;

    @Autowired
    UserService userService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private TransactionRace race;
    private String slug;
    private long memberId;

    @BeforeEach
    void setUp() {
        race = new TransactionRace(transactionTemplate);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "assign-race-" + suffix + "@example.com";
        slug = "assign-race-" + suffix;
        memberId =
                userService.createAccount(email, "member1234!", "경합", Role.USER).id();
        jdbc.update("INSERT INTO category (slug, name, sort_order) VALUES (?, ?, 7000)", slug, "경합 테스트");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        race.close();
        jdbc.update("DELETE FROM category_admin WHERE user_id = ?", memberId);
        jdbc.update("DELETE FROM users WHERE id = ?", memberId);
        jdbc.update("DELETE FROM category WHERE slug = ?", slug);
    }

    @Test
    @DisplayName("삭제가 먼저 커밋 전이면 그 카테고리를 담당으로 지정하는 요청은 기다렸다가 CategoryNotFoundException이고, 회원은 USER로 남는다")
    void assignWaitsForDeleteThenNotFound() throws Exception {
        Future<?> deleting = race.holdFirst(() -> adminCategoryService.delete(slug));

        Future<AdminUserResponse> assigning =
                race.submit(() -> adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(slug)));

        race.assertBlocked(assigning);
        race.releaseFirst();
        deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> assigning.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CategoryNotFoundException.class);
        assertThat(isDeleted()).isTrue();
        assertThat(roleOf(memberId)).isEqualTo("USER");
        assertThat(mappingCount()).isZero();
    }

    @Test
    @DisplayName("담당 지정이 먼저 커밋 전이면 삭제는 기다렸다가 CategoryHasSoleAdminException이고, 카테고리와 담당이 남는다 (R10)")
    void deleteWaitsForAssignThenSoleAdmin() throws Exception {
        Future<?> assigning =
                race.holdFirst(() -> adminUserService.changeRole(memberId, Role.CATEGORY_ADMIN, List.of(slug)));

        Future<?> deleting = race.submit(() -> adminCategoryService.delete(slug));

        race.assertBlocked(deleting);
        race.releaseFirst();
        assigning.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CategoryHasSoleAdminException.class);
        assertThat(isDeleted()).isFalse();
        assertThat(roleOf(memberId)).isEqualTo("CATEGORY_ADMIN");
        assertThat(mappingCount()).isEqualTo(1);
    }

    private boolean isDeleted() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM category WHERE slug = ?", Boolean.class, slug));
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
    }

    private long mappingCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM category_admin WHERE user_id = ?", Long.class, memberId);
        return count == null ? 0 : count;
    }
}
