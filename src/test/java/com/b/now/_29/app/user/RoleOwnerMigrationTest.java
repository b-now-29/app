package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 개편 마이그레이션이 기존 ADMIN을 OWNER와 카테고리 관리자로 나누는지 확인한다 (role-owner-category-admin R1, R3, D2, D5, D9).
 *
 * <p>별도 스키마에 V14까지 올리고 회원·카테고리를 넣은 뒤 나머지를 적용한다. 그래서 새 마이그레이션은 스키마 이름 ({@code public.})을 쓰지 않아야
 * 한다. 담당 매핑 테이블은 {@code category_admin (user_id, category_id)}다.
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
// Flyway는 자기 커넥션으로 마이그레이션하므로, 테스트 트랜잭션 없이 넣은 행이 바로 보이게 한다
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoleOwnerMigrationTest {

    private static final String SCHEMA = "role_owner_migration_test";
    private static final String HASH = "$2a$10$hash";

    @Autowired
    DataSource dataSource;

    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        flyway().target("14").load().migrate();
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("id가 가장 작은 ADMIN은 OWNER가 되고, 넣은 순서와 상관없다 (R3)")
    void smallestAdminBecomesOwner() {
        insertUser(30L, "c@example.com", "ADMIN", 3);
        insertUser(10L, "a@example.com", "ADMIN", 1);
        insertUser(20L, "b@example.com", "USER", 2);

        flyway().load().migrate();

        assertThat(roleOf(10L)).isEqualTo("OWNER");
        assertThat(roleOf(30L)).isEqualTo("CATEGORY_ADMIN");
        assertThat(roleOf(20L)).isEqualTo("USER");
    }

    @Test
    @DisplayName("나머지 ADMIN은 CATEGORY_ADMIN이 되어 삭제되지 않은 모든 카테고리를 담당하고, 삭제된 카테고리는 담당하지 않는다 (R3)")
    void otherAdminsManageAllActiveCategories() {
        insertUser(10L, "a@example.com", "ADMIN", 1);
        insertUser(30L, "c@example.com", "ADMIN", 3);
        insertUser(40L, "d@example.com", "ADMIN", 4);
        insertUser(20L, "b@example.com", "USER", 2);
        long deleted = insertCategory("migration-deleted", true);

        flyway().load().migrate();

        List<Long> activeCategoryIds = jdbc.queryForList(
                "SELECT id FROM " + SCHEMA + ".category WHERE deleted_at IS NULL ORDER BY id", Long.class);
        assertThat(activeCategoryIds).isNotEmpty();
        assertThat(managedCategoryIds(30L)).containsExactlyElementsOf(activeCategoryIds);
        assertThat(managedCategoryIds(40L)).containsExactlyElementsOf(activeCategoryIds);
        assertThat(managedCategoryIds(30L)).doesNotContain(deleted);
        assertThat(managedCategoryIds(10L)).as("OWNER는 매핑이 없다").isEmpty();
        assertThat(managedCategoryIds(20L)).as("USER는 매핑이 없다").isEmpty();
    }

    @Test
    @DisplayName("삭제되지 않은 카테고리가 없으면 나머지 ADMIN은 USER가 된다. OWNER는 그대로 정해진다 (D9)")
    void otherAdminsBecomeUsersWithoutActiveCategories() {
        insertUser(10L, "a@example.com", "ADMIN", 1);
        insertUser(30L, "c@example.com", "ADMIN", 3);
        jdbc.update("UPDATE " + SCHEMA + ".category SET deleted_at = now()");

        flyway().load().migrate();

        assertThat(roleOf(10L)).isEqualTo("OWNER");
        assertThat(roleOf(30L)).isEqualTo("USER");
        assertThat(managedCategoryIds(30L)).isEmpty();
    }

    @Test
    @DisplayName("ADMIN이 없으면 OWNER도 생기지 않고 회원 역할은 그대로다 (R3)")
    void noAdminMeansNoOwner() {
        insertUser(10L, "a@example.com", "USER", 1);

        flyway().load().migrate();

        assertThat(roleOf(10L)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".users WHERE role = 'OWNER'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("마이그레이션 후 ADMIN은 role로 쓸 수 없다 (R1)")
    void adminRoleIsGone() {
        flyway().load().migrate();

        assertThatThrownBy(() -> insertUser(50L, "e@example.com", "ADMIN", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".users WHERE role = 'ADMIN'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("마이그레이션 후 OWNER는 두 명이 될 수 없고, CATEGORY_ADMIN은 여럿일 수 있다 (R2, D5)")
    void ownerIsUnique() {
        insertUser(10L, "a@example.com", "ADMIN", 1);
        flyway().load().migrate();

        assertThatThrownBy(() -> insertUser(50L, "e@example.com", "OWNER", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertUser(60L, "f@example.com", "CATEGORY_ADMIN", 6);
        insertUser(70L, "g@example.com", "CATEGORY_ADMIN", 7);
    }

    @Test
    @DisplayName("같은 (회원, 카테고리) 매핑은 두 번 넣을 수 없다 (API 계약: 복합 유니크)")
    void mappingIsUnique() {
        insertUser(10L, "a@example.com", "ADMIN", 1);
        insertUser(30L, "c@example.com", "ADMIN", 3);
        flyway().load().migrate();
        long categoryId = managedCategoryIds(30L).getFirst();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO " + SCHEMA + ".category_admin (user_id, category_id) VALUES (?, ?)",
                        30L,
                        categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration");
    }

    private void insertUser(long id, String email, String role, int tag) {
        jdbc.update(
                "INSERT INTO " + SCHEMA
                        + ".users (id, email, password_hash, role, nickname, tag) VALUES (?, ?, ?, ?, ?, ?)",
                id,
                email,
                HASH,
                role,
                "회원",
                tag);
    }

    private long insertCategory(String slug, boolean deleted) {
        Long id = jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".category (slug, name, sort_order, deleted_at)"
                        + " VALUES (?, ?, 9000, CASE WHEN ? THEN now() END) RETURNING id",
                Long.class,
                slug,
                slug,
                deleted);
        return id == null ? 0 : id;
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("SELECT role FROM " + SCHEMA + ".users WHERE id = ?", String.class, userId);
    }

    private List<Long> managedCategoryIds(long userId) {
        return jdbc.queryForList(
                "SELECT category_id FROM " + SCHEMA + ".category_admin WHERE user_id = ? ORDER BY category_id",
                Long.class,
                userId);
    }
}
