package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 회원 관리 (docs/features/admin-members.md R2, R3, R4, D4, D5)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code User.getCreatedAt(): Instant} — 두 생성자 모두 {@code Instant.now()}로 채운다. 기존 행은 null일 수 있다 (D4)
 *   <li>{@code User.changeRole(Role newRole, long actorId): void} — {@code actorId}가 자기 id이고 {@code newRole}이 USER면
 *       {@code SelfDemotionNotAllowedException}을 던지고 role을 바꾸지 않는다. 그 밖에는 role을 바꾼다 (같은 role이면 변화 없음)
 *   <li>{@code com.b.now._29.app.user.SelfDemotionNotAllowedException(long userId)} — RuntimeException, ApiExceptionHandler에서
 *       409로 변환한다
 * </ul>
 *
 * <p>닉네임 (docs/features/nickname.md R2, R5, R7)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>생성자 {@code User(String email, String passwordHash, Nickname nickname, int tag)}, {@code User(String email,
 *       String passwordHash, Role role, Nickname nickname, int tag)} — 기존 2·3인자 생성자는 없앤다. tag가 1보다 작으면
 *       {@code IllegalArgumentException}
 *   <li>{@code getNickname(): String}, {@code getTag(): int}
 *   <li>(D11) {@code hasNickname(Nickname nickname): boolean} — 지금 닉네임과 같은지 판단만 한다 (대소문자 구분)
 *   <li>(D12) {@code changeNickname(Nickname nickname, int tag): void} — 닉네임과 번호를 바꾸는 엔티티 메서드. 닉네임 변경은 JPQL
 *       update가 아니라 이 메서드 + {@code @DynamicUpdate}로 저장한다. tag가 1보다 작으면 {@code IllegalArgumentException}이고
 *       아무것도 바꾸지 않는다
 *   <li>(Standards 리뷰) 닉네임·번호는 {@code @Embedded NicknameTag nicknameTag} 필드로 둔다. {@code getNickname()}, {@code
 *       getTag()}는 그대로 두고, {@code getNicknameTag(): NicknameTag}를 추가한다
 * </ul>
 */
class UserTest {

    private static final long ADMIN_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final Nickname NICKNAME = new Nickname("철수");

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 정규화한다 (R2)")
    void normalizesEmail() {
        assertThat(User.normalizeEmail("  User@Example.COM\t")).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("사용자를 만들면 이메일이 정규화되어 저장된다 (R2)")
    void storesNormalizedEmail() {
        User user = new User("  User@Example.COM ", "hash", NICKNAME, 1);

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("새로 만든 사용자의 role은 USER이고 관리자가 아니다 (R1)")
    void newUserIsUser() {
        User user = new User("user@example.com", "hash", NICKNAME, 1);

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("role을 ADMIN으로 만든 사용자는 관리자다 (R9)")
    void adminUserIsAdmin() {
        User user = new User("  Admin@Example.COM ", "hash", Role.ADMIN, NICKNAME, 1);

        assertThat(user.getEmail()).isEqualTo("admin@example.com");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isAdmin()).isTrue();
    }

    // 가입 시각 (R2, D4)

    @Test
    @DisplayName("새로 만든 사용자는 만든 시각이 createdAt에 채워진다 (R2, D4)")
    void newUserHasCreatedAt() {
        Instant before = Instant.now();

        User user = new User("user@example.com", "hash", NICKNAME, 1);
        User admin = new User("admin@example.com", "hash", Role.ADMIN, NICKNAME, 2);

        Instant after = Instant.now();
        assertThat(user.getCreatedAt()).isNotNull().isBetween(before, after);
        assertThat(admin.getCreatedAt()).isNotNull().isBetween(before, after);
    }

    // role 변경 (R3, R4, D5)

    @Test
    @DisplayName("관리자는 다른 USER 회원을 ADMIN으로 지정할 수 있다 (R3)")
    void promotesOtherUser() {
        User other = withId(new User("other@example.com", "hash", NICKNAME, 1), OTHER_ID);

        other.changeRole(Role.ADMIN, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.ADMIN);
        assertThat(other.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("관리자는 다른 ADMIN 회원을 USER로 해제할 수 있다 (R3)")
    void demotesOtherAdmin() {
        User other = withId(new User("other@example.com", "hash", Role.ADMIN, NICKNAME, 1), OTHER_ID);

        other.changeRole(Role.USER, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.USER);
        assertThat(other.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("이미 같은 role로 바꾸는 요청은 변화 없이 받아들인다 (R3)")
    void sameRoleIsNoOp() {
        User other = withId(new User("other@example.com", "hash", NICKNAME, 1), OTHER_ID);

        other.changeRole(Role.USER, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("자기 자신을 USER로 바꾸면 SelfDemotionNotAllowedException이고 role은 그대로 ADMIN이다 (R4, D2, D5)")
    void rejectsSelfDemotion() {
        User self = withId(new User("admin@example.com", "hash", Role.ADMIN, NICKNAME, 1), ADMIN_ID);

        assertThatThrownBy(() -> self.changeRole(Role.USER, ADMIN_ID))
                .isInstanceOf(SelfDemotionNotAllowedException.class);

        assertThat(self.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("자기 자신을 ADMIN으로 지정하는 요청은 변화 없이 받아들인다 (R4)")
    void allowsSelfKeepingAdmin() {
        User self = withId(new User("admin@example.com", "hash", Role.ADMIN, NICKNAME, 1), ADMIN_ID);

        self.changeRole(Role.ADMIN, ADMIN_ID);

        assertThat(self.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("id가 아직 없는 사용자도 다른 회원으로 보고 role을 바꾼다 (null id 비교에서 예외가 나지 않는다)")
    void unsavedUserIsNotSelf() {
        User unsaved = new User("new@example.com", "hash", Role.ADMIN, NICKNAME, 1);

        unsaved.changeRole(Role.USER, ADMIN_ID);

        assertThat(unsaved.getRole()).isEqualTo(Role.USER);
    }

    // 닉네임 (nickname.md R2, R5, R7)

    @Test
    @DisplayName("만든 사용자는 정규화한 닉네임과 번호를 가진다 (nickname R1, R2)")
    void storesNicknameAndTag() {
        User user = new User("user@example.com", "hash", new Nickname("  철수 "), 3);
        User admin = new User("admin@example.com", "hash", Role.ADMIN, new Nickname("영희"), 1);

        assertThat(user.getNickname()).isEqualTo("철수");
        assertThat(user.getTag()).isEqualTo(3);
        assertThat(admin.getNickname()).isEqualTo("영희");
        assertThat(admin.getTag()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("번호가 1보다 작으면 만들 수 없다 (nickname 데이터: tag 1 이상)")
    void rejectsTagBelowOne(int tag) {
        assertThatThrownBy(() -> new User("user@example.com", "hash", NICKNAME, tag))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User("user@example.com", "hash", Role.ADMIN, NICKNAME, tag))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("닉네임과 번호는 NicknameTag 값 객체로 묶여 있다 (Standards 리뷰)")
    void exposesNicknameTag() {
        User user = new User("user@example.com", "hash", new Nickname("  철수 "), 3);

        assertThat(user.getNicknameTag()).isEqualTo(new NicknameTag("철수", 3));
    }

    // 닉네임 변경 (nickname R5, D12)

    @Test
    @DisplayName("changeNickname은 닉네임과 번호를 함께 바꾸고 다른 값은 그대로 둔다 (nickname R5, D12)")
    void changesNicknameAndTag() {
        User user = withId(new User("user@example.com", "hash", Role.ADMIN, NICKNAME, 2), OTHER_ID);

        user.changeNickname(new Nickname("  영희 "), 5);

        assertThat(user.getNickname()).isEqualTo("영희");
        assertThat(user.getTag()).isEqualTo(5);
        assertThat(user.getNicknameTag()).isEqualTo(new NicknameTag("영희", 5));
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getId()).isEqualTo(OTHER_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("changeNickname의 번호가 1보다 작으면 거부하고 닉네임과 번호를 바꾸지 않는다 (nickname 데이터)")
    void changeNicknameRejectsTagBelowOne(int tag) {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThatThrownBy(() -> user.changeNickname(new Nickname("영희"), tag))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getNickname()).isEqualTo("철수");
        assertThat(user.getTag()).isEqualTo(2);
    }

    // "같은 닉네임이면 변경 없음" 판단 (nickname R5, D11)

    @Test
    @DisplayName("지금과 같은 닉네임이면 hasNickname은 참이다 (nickname R5, D11)")
    void hasSameNickname() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThat(user.hasNickname(new Nickname("철수"))).isTrue();
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 닉네임도 같은 닉네임이다 (nickname R1, R5, D11)")
    void hasSameNicknameAfterStrip() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThat(user.hasNickname(new Nickname("  철수 "))).isTrue();
    }

    @Test
    @DisplayName("다른 닉네임이면 hasNickname은 거짓이다 (nickname R5, D11)")
    void doesNotHaveOtherNickname() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThat(user.hasNickname(new Nickname("영희"))).isFalse();
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임은 다른 닉네임이다 (nickname R5, R7, D11)")
    void caseDifferentNicknameIsOther() {
        User user = new User("user@example.com", "hash", new Nickname("Kim"), 2);

        assertThat(user.hasNickname(new Nickname("kim"))).isFalse();
        assertThat(user.hasNickname(new Nickname("Kim"))).isTrue();
    }

    @Test
    @DisplayName("hasNickname은 판단만 하고 닉네임과 번호를 바꾸지 않는다 (D11)")
    void hasNicknameDoesNotChangeState() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        user.hasNickname(new Nickname("영희"));

        assertThat(user.getNickname()).isEqualTo("철수");
        assertThat(user.getTag()).isEqualTo(2);
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
