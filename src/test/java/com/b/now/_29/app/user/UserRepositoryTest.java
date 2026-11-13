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

/**
 * 회원 관리 (docs/features/admin-members.md R1, R2, D4)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code V12__add_users_created_at.sql} (이름은 자유): {@code users.created_at TIMESTAMPTZ} nullable, DEFAULT 없음, 기존 행은
 *       NULL
 *   <li>{@code User.createdAt} ({@code Instant}) 을 {@code created_at} 컬럼에 매핑
 *   <li>{@code UserRepository.findAllByOrderByIdAsc(): List<User>} — 회원 목록 id 오름차순 (R1)
 * </ul>
 *
 * <p>닉네임 (docs/features/nickname.md R2, R3, R7, D3, D7, 데이터)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code V13__*.sql}: {@code users.nickname VARCHAR(20) NOT NULL}, {@code users.tag INTEGER NOT NULL} (1 이상 CHECK),
 *       {@code CONSTRAINT uk_users_nickname_tag UNIQUE (nickname, tag)}. 기존 행 채우기는 {@code UserNicknameMigrationTest}
 *   <li>{@code UserRepository.findMaxTagByNickname(String nickname): Optional<Integer>} — 그 닉네임의 가장 큰 번호, 없으면 empty.
 *       대소문자를 구분한다 (R7)
 *   <li>(D12) {@code UserRepository.updateNickname}과 Repository의 {@code @Transactional}은 지운다. {@code User}에 {@code
 *       @DynamicUpdate}를 붙여 엔티티 메서드 {@code changeNickname}으로 바꾼 컬럼만 UPDATE한다
 *   <li>(Standards 리뷰) {@code User.nicknameTag} ({@code @Embedded NicknameTag})가 {@code nickname}, {@code tag} 컬럼에 매핑된다
 * </ul>
 */
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
    @DisplayName("대소문자만 다른 이메일은 DB 제약으로도 저장할 수 없다 (R2)")
    void rejectsEmailDifferingOnlyByCase() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, NICKNAME, 1));
        User duplicate = new User("Repository@Example.COM", HASH, NICKNAME, 2);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("role 컬럼을 role 필드로 읽고 쓴다 (R1, D1)")
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
    @DisplayName("role 없이 넣은 기존 회원 행은 USER다 (R1, 마이그레이션 기본값)")
    void existingRowsDefaultToUser() {
        insertLegacyRow(EMAIL, "기존", 1);
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    // 가입 시각 (R2, D4)

    @Test
    @DisplayName("새 회원의 createdAt을 created_at 컬럼에 저장하고 다시 읽는다 (R2, D4)")
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
    @DisplayName("created_at 없이 넣은 기존 회원 행은 createdAt이 null이다 (R2, D4: DB DEFAULT 없음)")
    void existingRowsHaveNullCreatedAt() {
        insertLegacyRow(EMAIL, "기존", 1);
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getCreatedAt())
                .isNull();
    }

    // 회원 목록 (R1)

    @Test
    @DisplayName("회원 목록은 id 오름차순이다. 나중에 수정한 행도 순서가 바뀌지 않는다 (R1)")
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

    // 닉네임과 번호 (nickname R2, R3, R7, D3, D7)

    @Test
    @DisplayName("닉네임과 번호를 nickname, tag 컬럼에 저장하고 다시 읽는다 (nickname 데이터)")
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
    @DisplayName("그 닉네임을 쓰는 회원이 없으면 가장 큰 번호는 empty다 (nickname R3)")
    void maxTagOfUnusedNicknameIsEmpty() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("다른이름"), 1));

        assertThat(userRepository.findMaxTagByNickname("아무도없음")).isEmpty();
    }

    @Test
    @DisplayName("가장 큰 번호를 돌려주므로 중간의 빈 번호는 채우지 않는다 (nickname R3, D3)")
    void maxTagSkipsGaps() {
        userRepository.saveAndFlush(new User("gap-1@example.com", HASH, new Nickname("빈번호"), 1));
        userRepository.saveAndFlush(new User("gap-3@example.com", HASH, new Nickname("빈번호"), 3));
        userRepository.saveAndFlush(new User("gap-other@example.com", HASH, new Nickname("빈번호아님"), 9));

        assertThat(userRepository.findMaxTagByNickname("빈번호")).contains(3);
    }

    @Test
    @DisplayName("닉네임은 대소문자를 구분하므로 Kim과 kim의 번호는 따로 센다 (nickname R7)")
    void maxTagIsCaseSensitive() {
        userRepository.saveAndFlush(new User("kim-upper-1@example.com", HASH, new Nickname("Kim"), 1));
        userRepository.saveAndFlush(new User("kim-upper-2@example.com", HASH, new Nickname("Kim"), 2));
        userRepository.saveAndFlush(new User("kim-lower@example.com", HASH, new Nickname("kim"), 1));

        assertThat(userRepository.findMaxTagByNickname("Kim")).contains(2);
        assertThat(userRepository.findMaxTagByNickname("kim")).contains(1);
        assertThat(userRepository.findMaxTagByNickname("KIM")).isEmpty();
    }

    @Test
    @DisplayName("같은 닉네임은 번호가 다르면 함께 저장되고, 같은 번호는 닉네임이 다르면 함께 저장된다 (nickname R2)")
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
    @DisplayName("같은 (닉네임, 번호)는 DB 제약 uk_users_nickname_tag로 저장할 수 없다 (nickname R2, D4)")
    void rejectsDuplicateNicknameAndTag() {
        userRepository.saveAndFlush(new User("dup-1@example.com", HASH, new Nickname("철수"), 1));
        User duplicate = new User("dup-2@example.com", HASH, new Nickname("철수"), 1);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_nickname_tag");
    }

    @Test
    @DisplayName("이메일 중복 위반은 번호 제약과 다른 이름 users_email_key로 구분된다 (nickname 제약 사항)")
    void emailViolationHasDifferentConstraintName() {
        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname("철수"), 1));
        User duplicate = new User(EMAIL, HASH, new Nickname("영희"), 1);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_email_key")
                .hasMessageNotContaining("uk_users_nickname_tag");
    }

    @Test
    @DisplayName("닉네임과 번호는 NicknameTag 값 객체 (nicknameTag)로 매핑되어 JPQL로도 읽힌다 (Standards 리뷰: @Embeddable)")
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

    // 닉네임 변경 저장 (nickname 제약 사항: lost update, D12)

    @Test
    @DisplayName("엔티티 메서드로 닉네임만 바꿔 저장하면 읽은 뒤 다른 곳에서 바뀐 role을 덮어쓰지 않는다 (D12: @DynamicUpdate)")
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
    @DisplayName("엔티티 메서드로 같은 (닉네임, 번호)를 만들면 flush에서 uk_users_nickname_tag 위반이다 (nickname D4)")
    void changeNicknameRejectsDuplicateNicknameAndTag() {
        userRepository.saveAndFlush(new User("update-dup-1@example.com", HASH, new Nickname("영희"), 1));
        User other = userRepository.saveAndFlush(new User("update-dup-2@example.com", HASH, new Nickname("철수"), 1));

        other.changeNickname(new Nickname("영희"), 1);

        assertThatThrownBy(userRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_nickname_tag");
    }

    @Test
    @DisplayName("보조 평면 문자 20개 닉네임도 VARCHAR(20)에 저장된다. 길이 검증과 컬럼 길이가 모두 code point 기준이다 (nickname D7)")
    void storesTwentyCodePointNickname() {
        String nickname = "𝒜".repeat(20);

        userRepository.saveAndFlush(new User(EMAIL, HASH, new Nickname(nickname), 1));
        entityManager.clear();

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getNickname())
                .isEqualTo(nickname);
    }

    @Test
    @DisplayName("nickname 없이는 행을 넣을 수 없다 (nickname R6: NOT NULL)")
    void nicknameIsNotNull() {
        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("INSERT INTO users (email, password_hash, tag) VALUES (:email, :hash, 1)")
                        .setParameter("email", EMAIL)
                        .setParameter("hash", HASH)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("tag 없이는 행을 넣을 수 없다 (nickname R6: NOT NULL)")
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
    @DisplayName("tag가 0이면 DB 제약으로 넣을 수 없다 (nickname 데이터: 1 이상)")
    void tagMustBePositive() {
        assertThatThrownBy(() -> insertLegacyRow(EMAIL, "철수", 0)).isInstanceOf(RuntimeException.class);
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
