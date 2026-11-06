package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

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
}
