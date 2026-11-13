package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 */
class UserTest {

    private static final long ADMIN_ID = 1L;
    private static final long OTHER_ID = 2L;

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 정규화한다 (R2)")
    void normalizesEmail() {
        assertThat(User.normalizeEmail("  User@Example.COM\t")).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("사용자를 만들면 이메일이 정규화되어 저장된다 (R2)")
    void storesNormalizedEmail() {
        User user = new User("  User@Example.COM ", "hash");

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("새로 만든 사용자의 role은 USER이고 관리자가 아니다 (R1)")
    void newUserIsUser() {
        User user = new User("user@example.com", "hash");

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("role을 ADMIN으로 만든 사용자는 관리자다 (R9)")
    void adminUserIsAdmin() {
        User user = new User("  Admin@Example.COM ", "hash", Role.ADMIN);

        assertThat(user.getEmail()).isEqualTo("admin@example.com");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isAdmin()).isTrue();
    }

    // 가입 시각 (R2, D4)

    @Test
    @DisplayName("새로 만든 사용자는 만든 시각이 createdAt에 채워진다 (R2, D4)")
    void newUserHasCreatedAt() {
        Instant before = Instant.now();

        User user = new User("user@example.com", "hash");
        User admin = new User("admin@example.com", "hash", Role.ADMIN);

        Instant after = Instant.now();
        assertThat(user.getCreatedAt()).isNotNull().isBetween(before, after);
        assertThat(admin.getCreatedAt()).isNotNull().isBetween(before, after);
    }

    // role 변경 (R3, R4, D5)

    @Test
    @DisplayName("관리자는 다른 USER 회원을 ADMIN으로 지정할 수 있다 (R3)")
    void promotesOtherUser() {
        User other = withId(new User("other@example.com", "hash"), OTHER_ID);

        other.changeRole(Role.ADMIN, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.ADMIN);
        assertThat(other.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("관리자는 다른 ADMIN 회원을 USER로 해제할 수 있다 (R3)")
    void demotesOtherAdmin() {
        User other = withId(new User("other@example.com", "hash", Role.ADMIN), OTHER_ID);

        other.changeRole(Role.USER, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.USER);
        assertThat(other.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("이미 같은 role로 바꾸는 요청은 변화 없이 받아들인다 (R3)")
    void sameRoleIsNoOp() {
        User other = withId(new User("other@example.com", "hash"), OTHER_ID);

        other.changeRole(Role.USER, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("자기 자신을 USER로 바꾸면 SelfDemotionNotAllowedException이고 role은 그대로 ADMIN이다 (R4, D2, D5)")
    void rejectsSelfDemotion() {
        User self = withId(new User("admin@example.com", "hash", Role.ADMIN), ADMIN_ID);

        assertThatThrownBy(() -> self.changeRole(Role.USER, ADMIN_ID))
                .isInstanceOf(SelfDemotionNotAllowedException.class);

        assertThat(self.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("자기 자신을 ADMIN으로 지정하는 요청은 변화 없이 받아들인다 (R4)")
    void allowsSelfKeepingAdmin() {
        User self = withId(new User("admin@example.com", "hash", Role.ADMIN), ADMIN_ID);

        self.changeRole(Role.ADMIN, ADMIN_ID);

        assertThat(self.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("id가 아직 없는 사용자도 다른 회원으로 보고 role을 바꾼다 (null id 비교에서 예외가 나지 않는다)")
    void unsavedUserIsNotSelf() {
        User unsaved = new User("new@example.com", "hash", Role.ADMIN);

        unsaved.changeRole(Role.USER, ADMIN_ID);

        assertThat(unsaved.getRole()).isEqualTo(Role.USER);
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
