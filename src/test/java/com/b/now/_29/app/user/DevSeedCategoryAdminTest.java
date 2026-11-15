package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.TestcontainersConfiguration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * dev 더미 데이터 ({@code db/dev-seed/R__dev_seed.sql})의 카테고리 관리자 user20이 삭제되지 않은 담당 카테고리를 갖는지 확인한다
 * (role-owner-category-admin R9, D9, D17).
 *
 * <p>별도 스키마에 마이그레이션을 올린 뒤, 기존 카테고리 상태를 바꾸고 dev seed를 적용한다 ({@link RoleOwnerMigrationTest}와 같은 방식).
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
// Flyway는 자기 커넥션으로 마이그레이션하므로, 테스트 트랜잭션 없이 바꾼 행이 바로 보이게 한다
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DevSeedCategoryAdminTest {

    private static final String SCHEMA = "dev_seed_category_admin_test";
    private static final String MANAGER_EMAIL = "user20@dev.local";

    @Autowired
    DataSource dataSource;

    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        flyway("classpath:db/migration").load().migrate();
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("기존 카테고리가 있으면 user20은 CATEGORY_ADMIN이고 삭제되지 않은 카테고리만 1개 이상 담당한다 (R9)")
    void managerHasActiveCategories() {
        flyway("classpath:db/migration", "classpath:db/dev-seed").load().migrate();

        assertThat(roleOfManager()).isEqualTo("CATEGORY_ADMIN");
        assertThat(activeManagedCount()).isPositive();
        assertThat(deletedManagedCount()).isZero();
    }

    @Test
    @DisplayName("기존 카테고리가 모두 삭제돼 있어도 seed가 실패하지 않고, user20은 seed가 만든 카테고리를 담당한다 (D17)")
    void managerGetsSeededCategoryWhenNoneExistedBefore() {
        jdbc.update("UPDATE " + SCHEMA + ".category SET deleted_at = now()");

        flyway("classpath:db/migration", "classpath:db/dev-seed").load().migrate();

        assertThat(roleOfManager()).isEqualTo("CATEGORY_ADMIN");
        assertThat(activeManagedCount()).isPositive();
        assertThat(deletedManagedCount()).isZero();
    }

    private FluentConfiguration flyway(String... locations) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations(locations);
    }

    private String roleOfManager() {
        return jdbc.queryForObject(
                "SELECT role FROM " + SCHEMA + ".users WHERE email = ?", String.class, MANAGER_EMAIL);
    }

    private long activeManagedCount() {
        return managedCount("c.deleted_at IS NULL");
    }

    private long deletedManagedCount() {
        return managedCount("c.deleted_at IS NOT NULL");
    }

    private long managedCount(String condition) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + SCHEMA + ".category_admin ca JOIN " + SCHEMA
                        + ".category c ON c.id = ca.category_id JOIN " + SCHEMA
                        + ".users u ON u.id = ca.user_id WHERE u.email = ? AND " + condition,
                Long.class,
                MANAGER_EMAIL);
        return count == null ? 0 : count;
    }
}
