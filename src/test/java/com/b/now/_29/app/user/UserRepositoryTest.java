package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.b.now._29.app.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 회원 관리 (docs/features/admin-members.md R1, R2, D4)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code V12__add_users_created_at.sql} (이름은 자유): {@code users.created_at TIMESTAMPTZ} nullable, DEFAULT 없음, 기존 행은
 *       NULL
 *   <li>{@code User.createdAt} ({@code Instant}) 을 {@code created_at} 컬럼에 매핑
 *   <li>{@code UserRepository.findAllByOrderByIdAsc(): List<User>} — 회원 목록 id 오름차순 (R1)
 * </ul>
 */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    private static final String EMAIL = "repository@example.com";
    private static final String HASH = "$2a$10$hash";

    @Autowired
    UserRepository userRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("저장한 사용자를 이메일로 찾는다")
    void findsByEmail() {
        User saved = userRepository.saveAndFlush(new User(EMAIL, HASH));

        assertThat(userRepository.findByEmail(EMAIL)).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getEmail()).isEqualTo(EMAIL);
            assertThat(found.getPasswordHash()).isEqualTo(HASH);
        });
    }

    @Test
    @DisplayName("저장된 이메일이면 existsByEmail은 참, 아니면 거짓이다")
    void existsByEmail() {
        userRepository.saveAndFlush(new User(EMAIL, HASH));

        assertThat(userRepository.existsByEmail(EMAIL)).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("대소문자만 다른 이메일은 DB 제약으로도 저장할 수 없다 (R2)")
    void rejectsEmailDifferingOnlyByCase() {
        userRepository.saveAndFlush(new User(EMAIL, HASH));
        User duplicate = new User("Repository@Example.COM", HASH);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("role 컬럼을 role 필드로 읽고 쓴다 (R1, D1)")
    void mapsRoleColumn() {
        userRepository.saveAndFlush(new User(EMAIL, HASH));
        userRepository.saveAndFlush(new User("admin-repository@example.com", HASH, Role.ADMIN));
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(userRepository
                        .findByEmail("admin-repository@example.com")
                        .orElseThrow()
                        .getRole())
                .isEqualTo(Role.ADMIN);
        assertThat(entityManager
                        .createNativeQuery("SELECT role FROM users WHERE email = :email")
                        .setParameter("email", "admin-repository@example.com")
                        .getSingleResult())
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("role 없이 넣은 기존 회원 행은 USER다 (R1, 마이그레이션 기본값)")
    void existingRowsDefaultToUser() {
        entityManager
                .createNativeQuery("INSERT INTO users (email, password_hash) VALUES (:email, :hash)")
                .setParameter("email", EMAIL)
                .setParameter("hash", HASH)
                .executeUpdate();
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    // 가입 시각 (R2, D4)

    @Test
    @DisplayName("새 회원의 createdAt을 created_at 컬럼에 저장하고 다시 읽는다 (R2, D4)")
    void mapsCreatedAtColumn() {
        User saved = userRepository.saveAndFlush(new User(EMAIL, HASH));
        entityManager.clear();

        assertThat(entityManager
                        .createNativeQuery("SELECT created_at FROM users WHERE email = :email")
                        .setParameter("email", EMAIL)
                        .getSingleResult())
                .isNotNull();
        // TIMESTAMPTZ는 마이크로초까지 저장하므로 그 아래 자릿수는 비교하지 않는다
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getCreatedAt())
                .isNotNull()
                .isCloseTo(saved.getCreatedAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("created_at 없이 넣은 기존 회원 행은 createdAt이 null이다 (R2, D4: DB DEFAULT 없음)")
    void existingRowsHaveNullCreatedAt() {
        entityManager
                .createNativeQuery("INSERT INTO users (email, password_hash) VALUES (:email, :hash)")
                .setParameter("email", EMAIL)
                .setParameter("hash", HASH)
                .executeUpdate();
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getCreatedAt())
                .isNull();
    }

    // 회원 목록 (R1)

    @Test
    @DisplayName("회원 목록은 id 오름차순이다. 나중에 수정한 행도 순서가 바뀌지 않는다 (R1)")
    void listsUsersByIdAscending() {
        User first = userRepository.saveAndFlush(new User("order-1@example.com", HASH));
        User second = userRepository.saveAndFlush(new User("order-2@example.com", HASH));
        User third = userRepository.saveAndFlush(new User("order-3@example.com", HASH));
        // UPDATE는 PostgreSQL에서 행의 물리 위치를 뒤로 옮기므로, 정렬 없는 조회라면 first가 끝으로 갈 수 있다
        first.changeRole(Role.ADMIN, third.getId() + 1000);
        userRepository.saveAndFlush(first);
        entityManager.clear();

        List<Long> ids = userRepository.findAllByOrderByIdAsc().stream()
                .filter(user -> user.getEmail().startsWith("order-"))
                .map(User::getId)
                .toList();

        assertThat(ids).containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(userRepository.findAllByOrderByIdAsc())
                .extracting(User::getId)
                .isSorted();
    }
}
