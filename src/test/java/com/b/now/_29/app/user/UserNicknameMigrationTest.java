package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.b.now._29.app.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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
 * V13 마이그레이션이 기존 회원을 채우는지 확인한다 (docs/features/nickname.md R6, D5, 데이터).
 *
 * <p>테스트 DB의 기본 스키마는 이미 최신까지 마이그레이션되어 기존 회원이 없으므로, 별도 스키마에 V12까지 올리고 회원을 넣은 뒤 나머지를 적용한다. 그래서 V13은 스키마
 * 이름 ({@code public.})을 쓰지 않아야 한다.
 *
 * <p>구현할 것: {@code V13__*.sql} — 컬럼 추가 → 기존 행을 {@code 회원}, id 순 1, 2, 3…으로 채우기 → NOT NULL, {@code CHECK (tag >=
 * 1)}, {@code CONSTRAINT uk_users_nickname_tag UNIQUE (nickname, tag)}.
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
// Flyway는 자기 커넥션으로 마이그레이션하므로, 테스트 트랜잭션 없이 넣은 행이 바로 보이게 한다
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserNicknameMigrationTest {

    private static final String SCHEMA = "nickname_migration_test";
    private static final String HASH = "$2a$10$hash";

    @Autowired
    DataSource dataSource;

    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        flyway().target("12").load().migrate();
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("기존 회원은 닉네임 회원, 번호는 넣은 순서가 아니라 id 순으로 1, 2, 3이다 (nickname R6, D5)")
    void fillsExistingUsersInIdOrder() {
        // 넣은 순서와 id 순서를 다르게 해 id 기준인지 확인한다
        insertUser(30L, "c@example.com", "USER");
        insertUser(10L, "a@example.com", "ADMIN");
        insertUser(20L, "b@example.com", "USER");

        flyway().load().migrate();

        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT id, nickname, tag FROM " + SCHEMA + ".users ORDER BY id");
        assertThat(rows)
                .extracting(row -> ((Number) row.get("id")).longValue(), row -> row.get("nickname"), row -> ((Number)
                                row.get("tag"))
                        .intValue())
                .containsExactly(tuple(10L, "회원", 1), tuple(20L, "회원", 2), tuple(30L, "회원", 3));
    }

    @Test
    @DisplayName("회원이 없어도 마이그레이션된다 (nickname R6)")
    void migratesEmptyTable() {
        flyway().load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".users", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("마이그레이션 후 nickname은 VARCHAR(20) NOT NULL, tag는 INTEGER NOT NULL이다 (nickname R6, 데이터)")
    void columnsAreNotNull() {
        insertUser(1L, "a@example.com", "USER");

        flyway().load().migrate();

        List<Map<String, Object>> columns = jdbc.queryForList(
                "SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = 'users' AND column_name IN ('nickname', 'tag')"
                        + " ORDER BY column_name",
                SCHEMA);
        assertThat(columns)
                .extracting(
                        row -> row.get("column_name"),
                        row -> row.get("data_type"),
                        row -> row.get("character_maximum_length") == null
                                ? null
                                : ((Number) row.get("character_maximum_length")).intValue(),
                        row -> row.get("is_nullable"))
                .containsExactly(tuple("nickname", "character varying", 20, "NO"), tuple("tag", "integer", null, "NO"));
    }

    @Test
    @DisplayName("마이그레이션 후 UNIQUE (nickname, tag) 제약 이름은 uk_users_nickname_tag이고 이메일 제약과 이름이 다르다 (nickname 데이터, 제약 사항)")
    void uniqueConstraintNames() {
        flyway().load().migrate();

        List<String> uniqueConstraints = jdbc.queryForList(
                "SELECT c.conname FROM pg_constraint c"
                        + " JOIN pg_class t ON c.conrelid = t.oid"
                        + " JOIN pg_namespace n ON t.relnamespace = n.oid"
                        + " WHERE n.nspname = ? AND t.relname = 'users' AND c.contype = 'u'",
                String.class,
                SCHEMA);
        assertThat(uniqueConstraints).contains("uk_users_nickname_tag", "users_email_key");
    }

    @Test
    @DisplayName("마이그레이션 후 채운 (회원, 번호)와 같은 조합은 넣을 수 없고, 다음 번호는 넣을 수 있다 (nickname R2, R3)")
    void uniqueConstraintAppliesToFilledRows() {
        insertUser(1L, "a@example.com", "USER");
        insertUser(2L, "b@example.com", "USER");
        flyway().load().migrate();

        // id를 직접 넣은 행은 identity 시퀀스를 움직이지 않으므로, 여기서도 id를 지정해 기본 키 충돌을 피한다.
        // 그래야 첫 INSERT가 닉네임 제약 때문에만 실패한다
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO " + SCHEMA
                                + ".users (id, email, password_hash, nickname, tag) VALUES (3, ?, ?, '회원', 2)",
                        "c@example.com",
                        HASH))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_nickname_tag");
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".users (id, email, password_hash, nickname, tag) VALUES (4, ?, ?, '회원', 3)",
                "d@example.com",
                HASH);
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration");
    }

    private void insertUser(long id, String email, String role) {
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                id,
                email,
                HASH,
                role);
    }
}
