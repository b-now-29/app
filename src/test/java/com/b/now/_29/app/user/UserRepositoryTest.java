package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

/** 회원 엔티티의 컬럼 매핑, 조회 쿼리, DB 제약을 실제 PostgreSQL로 확인한다. */
@Tag("integration")
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    private static final String EMAIL = "repository@example.com";
    private static final String HASH = "$2a$10$hash";
    private static final Nickname NICKNAME = new Nickname("저장소");
    private static final String INSERT_LEGACY_ROW =
            "INSERT INTO users (email, password_hash, nickname, tag) VALUES (:email, :hash, :nickname, :tag)";

    @Autowired
    UserRepository userRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("저장한 사용자를 이메일로 찾는다")
    void findsByEmail() {
        User saved = userRepository.saveAndFlush(user(EMAIL));

        assertThat(userRepository.findByEmail(EMAIL)).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getEmail()).isEqualTo(EMAIL);
            assertThat(found.getPasswordHash()).isEqualTo(HASH);
        });
    }

    @Test
    @DisplayName("저장된 이메일이면 existsByEmail은 참, 아니면 거짓이다")
    void existsByEmail() {
        userRepository.saveAndFlush(user(EMAIL));

        assertThat(userRepository.existsByEmail(EMAIL)).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("대소문자만 다른 이메일은 DB 제약으로도 저장할 수 없다")
    void rejectsEmailDifferingOnlyByCase() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, NICKNAME, 1));
        User duplicate = new User("Repository@Example.COM", HASH, NICKNAME, 2);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("role 컬럼을 role 필드로 읽고 쓴다")
    void mapsRoleColumn() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, NICKNAME, 1));
        userRepository.saveAndFlush(new User("admin-repository@example.com", HASH, Role.ADMIN, NICKNAME, 2));
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
    @DisplayName("role 없이 넣은 기존 회원 행은 마이그레이션 기본값인 USER다")
    void existingRowsDefaultToUser() {
        insertLegacyRow(EMAIL, "기존", 1);
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    // 가입 시각

    @Test
    @DisplayName("새 회원의 createdAt을 created_at 컬럼에 저장하고 다시 읽는다")
    void mapsCreatedAtColumn() {
        User saved = userRepository.saveAndFlush(user(EMAIL));
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
    @DisplayName("created_at 없이 넣은 기존 회원 행은 DB DEFAULT가 없어 createdAt이 null이다")
    void existingRowsHaveNullCreatedAt() {
        insertLegacyRow(EMAIL, "기존", 1);
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getCreatedAt())
                .isNull();
    }

    // 회원 목록

    @Test
    @DisplayName("회원 목록은 id 오름차순이다. 나중에 수정한 행도 순서가 바뀌지 않는다")
    void listsUsersByIdAscending() {
        User first = userRepository.saveAndFlush(new User("order-1@example.com", HASH, NICKNAME, 1));
        User second = userRepository.saveAndFlush(new User("order-2@example.com", HASH, NICKNAME, 2));
        User third = userRepository.saveAndFlush(new User("order-3@example.com", HASH, NICKNAME, 3));
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

    // 닉네임과 번호

    @Test
    @DisplayName("닉네임과 번호를 nickname, tag 컬럼에 저장하고 다시 읽는다")
    void mapsNicknameAndTagColumns() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("철수"), 7));
        entityManager.clear();

        User found = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(found.getNickname()).isEqualTo("철수");
        assertThat(found.getTag()).isEqualTo(7);
        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT nickname, tag FROM users WHERE email = :email")
                .setParameter("email", EMAIL)
                .getSingleResult();
        assertThat(row[0]).isEqualTo("철수");
        assertThat(((Number) row[1]).intValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("그 닉네임을 쓰는 회원이 없으면 가장 큰 번호는 empty다")
    void maxTagOfUnusedNicknameIsEmpty() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("다른이름"), 1));

        assertThat(userRepository.findMaxTagByNickname("아무도없음")).isEmpty();
    }

    @Test
    @DisplayName("가장 큰 번호를 돌려주므로 중간의 빈 번호는 채우지 않는다")
    void maxTagSkipsGaps() {
        userRepository.saveAndFlush(new User("gap-1@example.com", HASH, new Nickname("빈번호"), 1));
        userRepository.saveAndFlush(new User("gap-3@example.com", HASH, new Nickname("빈번호"), 3));
        userRepository.saveAndFlush(new User("gap-other@example.com", HASH, new Nickname("빈번호아님"), 9));

        assertThat(userRepository.findMaxTagByNickname("빈번호")).contains(3);
    }

    @Test
    @DisplayName("닉네임은 대소문자를 구분하므로 Kim과 kim의 번호는 따로 센다")
    void maxTagIsCaseSensitive() {
        userRepository.saveAndFlush(new User("kim-upper-1@example.com", HASH, new Nickname("Kim"), 1));
        userRepository.saveAndFlush(new User("kim-upper-2@example.com", HASH, new Nickname("Kim"), 2));
        userRepository.saveAndFlush(new User("kim-lower@example.com", HASH, new Nickname("kim"), 1));

        assertThat(userRepository.findMaxTagByNickname("Kim")).contains(2);
        assertThat(userRepository.findMaxTagByNickname("kim")).contains(1);
        assertThat(userRepository.findMaxTagByNickname("KIM")).isEmpty();
    }

    @Test
    @DisplayName("같은 닉네임은 번호가 다르면 함께 저장되고, 같은 번호는 닉네임이 다르면 함께 저장된다")
    void allowsSameNicknameOrSameTag() {
        assertThatCode(() -> {
                    userRepository.saveAndFlush(new User("same-1@example.com", HASH, new Nickname("철수"), 1));
                    userRepository.saveAndFlush(new User("same-2@example.com", HASH, new Nickname("철수"), 2));
                    userRepository.saveAndFlush(new User("same-3@example.com", HASH, new Nickname("영희"), 1));
                    userRepository.saveAndFlush(new User("same-4@example.com", HASH, new Nickname("Kim"), 1));
                    userRepository.saveAndFlush(new User("same-5@example.com", HASH, new Nickname("kim"), 1));
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 (닉네임, 번호)는 DB 제약 uk_users_nickname_tag로 저장할 수 없다")
    void rejectsDuplicateNicknameAndTag() {
        userRepository.saveAndFlush(new User("dup-1@example.com", HASH, new Nickname("철수"), 1));
        User duplicate = new User("dup-2@example.com", HASH, new Nickname("철수"), 1);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_nickname_tag");
    }

    @Test
    @DisplayName("이메일 중복은 재시도하지 않고 409로 바꿔야 하므로, 번호 제약과 다른 이름 users_email_key로 구분된다")
    void emailViolationHasDifferentConstraintName() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("철수"), 1));
        User duplicate = new User(EMAIL, HASH, new Nickname("영희"), 1);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_email_key")
                .hasMessageNotContaining("uk_users_nickname_tag");
    }

    @Test
    @DisplayName("닉네임과 번호는 NicknameTag 값 객체 (nicknameTag)로 매핑되어 JPQL로도 읽힌다")
    void mapsNicknameTagAsEmbeddable() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("철수"), 7));
        entityManager.clear();

        NicknameTag found = entityManager
                .createQuery("select u.nicknameTag from User u where u.email = :email", NicknameTag.class)
                .setParameter("email", EMAIL)
                .getSingleResult();

        assertThat(found).isEqualTo(new NicknameTag("철수", 7));
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getNicknameTag())
                .isEqualTo(new NicknameTag("철수", 7));
    }

    // 닉네임 변경 저장: 다른 트랜잭션이 바꾼 컬럼을 덮어쓰는 lost update가 없어야 한다

    @Test
    @DisplayName("엔티티 메서드로 닉네임만 바꿔 저장하면 바뀐 컬럼만 UPDATE되어, 읽은 뒤 다른 곳에서 바뀐 role을 덮어쓰지 않는다")
    void changeNicknameDoesNotOverwriteRoleChangedMeanwhile() {
        User saved = userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("철수"), 1));
        entityManager.clear();
        // 회원을 읽은 뒤 (영속 상태의 role은 USER) 관리자가 다른 트랜잭션에서 ADMIN으로 바꾼 상황
        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        entityManager
                .createNativeQuery("UPDATE users SET role = 'ADMIN' WHERE id = :id")
                .setParameter("id", saved.getId())
                .executeUpdate();

        loaded.changeNickname(new Nickname("영희"), 3);
        userRepository.flush();
        entityManager.clear();

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getNickname()).isEqualTo("영희");
        assertThat(found.getTag()).isEqualTo(3);
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.getEmail()).isEqualTo(EMAIL);
        assertThat(found.getPasswordHash()).isEqualTo(HASH);
    }

    @Test
    @DisplayName("엔티티 메서드로 같은 (닉네임, 번호)를 만들면 flush에서 uk_users_nickname_tag 위반이다")
    void changeNicknameRejectsDuplicateNicknameAndTag() {
        userRepository.saveAndFlush(new User("update-dup-1@example.com", HASH, new Nickname("영희"), 1));
        User other = userRepository.saveAndFlush(new User("update-dup-2@example.com", HASH, new Nickname("철수"), 1));

        other.changeNickname(new Nickname("영희"), 1);

        assertThatThrownBy(userRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_nickname_tag");
    }

    @Test
    @DisplayName("보조 평면 문자 20개 닉네임도 VARCHAR(20)에 저장된다. 길이 검증과 컬럼 길이가 모두 code point 기준이다")
    void storesTwentyCodePointNickname() {
        String nickname = "𝒜".repeat(20);

        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname(nickname), 1));
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getNickname())
                .isEqualTo(nickname);
    }

    @Test
    @DisplayName("nickname 컬럼은 NOT NULL이라 없이는 행을 넣을 수 없다")
    void nicknameIsNotNull() {
        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("INSERT INTO users (email, password_hash, tag) VALUES (:email, :hash, 1)")
                        .setParameter("email", EMAIL)
                        .setParameter("hash", HASH)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("tag 컬럼은 NOT NULL이라 없이는 행을 넣을 수 없다")
    void tagIsNotNull() {
        assertThatThrownBy(() -> entityManager
                        .createNativeQuery(
                                "INSERT INTO users (email, password_hash, nickname) VALUES (:email, :hash, '철수')")
                        .setParameter("email", EMAIL)
                        .setParameter("hash", HASH)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("번호는 1 이상이므로 tag가 0이면 DB 제약으로 넣을 수 없다")
    void tagMustBePositive() {
        assertThatThrownBy(() -> insertLegacyRow(EMAIL, "철수", 0)).isInstanceOf(RuntimeException.class);
    }

    // 토큰 버전

    @Test
    @DisplayName("회원 ID로 토큰 버전만 조회한다. 새 회원은 0이다")
    void findsTokenVersionById() {
        User saved = userRepository.saveAndFlush(user(EMAIL));
        entityManager.clear();

        assertThat(userRepository.findTokenVersionById(saved.getId())).contains(0);
    }

    @Test
    @DisplayName("없는 회원의 토큰 버전은 빈 값이다")
    void tokenVersionOfUnknownUserIsEmpty() {
        User saved = userRepository.saveAndFlush(user(EMAIL));

        assertThat(userRepository.findTokenVersionById(saved.getId() + 1000)).isEmpty();
    }

    @Test
    @DisplayName("token_version 없이 넣은 기존 회원 행은 토큰 버전이 0이다 (마이그레이션 기본값)")
    void existingRowsHaveTokenVersionZero() {
        insertLegacyRow(EMAIL, "기존", 1);
        entityManager.clear();

        User found = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(found.getTokenVersion()).isZero();
        assertThat(userRepository.findTokenVersionById(found.getId())).contains(0);
    }

    @Test
    @DisplayName("revokeTokens로 올린 토큰 버전을 token_version 컬럼에 저장하고, 읽은 뒤 다른 곳에서 바뀐 role을 덮어쓰지 않는다")
    void revokeTokensStoresVersionWithoutOverwritingRole() {
        User saved = userRepository.saveAndFlush(user(EMAIL));
        entityManager.clear();
        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        entityManager
                .createNativeQuery("UPDATE users SET role = 'ADMIN' WHERE id = :id")
                .setParameter("id", saved.getId())
                .executeUpdate();

        loaded.revokeTokens();
        userRepository.flush();
        entityManager.clear();

        assertThat(userRepository.findTokenVersionById(saved.getId())).contains(1);
        assertThat(((Number) entityManager
                                .createNativeQuery("SELECT token_version FROM users WHERE id = :id")
                                .setParameter("id", saved.getId())
                                .getSingleResult())
                        .intValue())
                .isEqualTo(1);
        assertThat(userRepository.findById(saved.getId()).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    private static User user(String email) {
        return new User(email, HASH, NICKNAME, 1);
    }

    private void insertLegacyRow(String email, String nickname, int tag) {
        entityManager
                .createNativeQuery(INSERT_LEGACY_ROW)
                .setParameter("email", email)
                .setParameter("hash", HASH)
                .setParameter("nickname", nickname)
                .setParameter("tag", tag)
                .executeUpdate();
    }
}
