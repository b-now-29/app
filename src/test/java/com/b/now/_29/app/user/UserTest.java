package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** 회원 엔티티의 생성 검증, role·닉네임 변경, 토큰 버전을 확인한다. */
class UserTest {

    private static final long ADMIN_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final Nickname NICKNAME = new Nickname("철수");

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 정규화한다")
    void normalizesEmail() {
        assertThat(User.normalizeEmail("  User@Example.COM\t")).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("사용자를 만들면 이메일이 정규화되어 저장된다")
    void storesNormalizedEmail() {
        User user = new User("  User@Example.COM ", "hash", NICKNAME, 1);

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("새로 만든 사용자의 role은 USER이고 관리자가 아니다")
    void newUserIsUser() {
        User user = new User("user@example.com", "hash", NICKNAME, 1);

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("role을 ADMIN으로 만든 사용자는 관리자다")
    void adminUserIsAdmin() {
        User user = new User("  Admin@Example.COM ", "hash", Role.ADMIN, NICKNAME, 1);

        assertThat(user.getEmail()).isEqualTo("admin@example.com");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isAdmin()).isTrue();
    }

    // 가입 시각

    @Test
    @DisplayName("새로 만든 사용자는 만든 시각이 createdAt에 채워진다")
    void newUserHasCreatedAt() {
        Instant before = Instant.now();

        User user = new User("user@example.com", "hash", NICKNAME, 1);
        User admin = new User("admin@example.com", "hash", Role.ADMIN, NICKNAME, 2);

        Instant after = Instant.now();
        assertThat(user.getCreatedAt()).isNotNull().isBetween(before, after);
        assertThat(admin.getCreatedAt()).isNotNull().isBetween(before, after);
    }

    // role 변경

    @Test
    @DisplayName("관리자는 다른 USER 회원을 ADMIN으로 지정할 수 있다")
    void promotesOtherUser() {
        User other = withId(new User("other@example.com", "hash", NICKNAME, 1), OTHER_ID);

        other.changeRole(Role.ADMIN, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.ADMIN);
        assertThat(other.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("관리자는 다른 ADMIN 회원을 USER로 해제할 수 있다")
    void demotesOtherAdmin() {
        User other = withId(new User("other@example.com", "hash", Role.ADMIN, NICKNAME, 1), OTHER_ID);

        other.changeRole(Role.USER, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.USER);
        assertThat(other.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("이미 같은 role로 바꾸는 요청은 변화 없이 받아들인다")
    void sameRoleIsNoOp() {
        User other = withId(new User("other@example.com", "hash", NICKNAME, 1), OTHER_ID);

        other.changeRole(Role.USER, ADMIN_ID);

        assertThat(other.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("자기 자신을 USER로 바꾸면 SelfDemotionNotAllowedException이고 role은 그대로 ADMIN이다")
    void rejectsSelfDemotion() {
        User self = withId(new User("admin@example.com", "hash", Role.ADMIN, NICKNAME, 1), ADMIN_ID);

        assertThatThrownBy(() -> self.changeRole(Role.USER, ADMIN_ID))
                .isInstanceOf(SelfDemotionNotAllowedException.class);

        assertThat(self.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("자기 자신을 ADMIN으로 지정하는 요청은 변화 없이 받아들인다")
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

    // 닉네임

    @Test
    @DisplayName("만든 사용자는 정규화한 닉네임과 번호를 가진다")
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
    @DisplayName("번호는 1부터 매기므로 1보다 작으면 만들 수 없다")
    void rejectsTagBelowOne(int tag) {
        assertThatThrownBy(() -> new User("user@example.com", "hash", NICKNAME, tag))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User("user@example.com", "hash", Role.ADMIN, NICKNAME, tag))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("닉네임과 번호는 늘 함께 다니므로 NicknameTag 값 객체로 묶여 있다")
    void exposesNicknameTag() {
        User user = new User("user@example.com", "hash", new Nickname("  철수 "), 3);

        assertThat(user.getNicknameTag()).isEqualTo(new NicknameTag("철수", 3));
    }

    // 닉네임 변경

    @Test
    @DisplayName("changeNickname은 닉네임과 번호를 함께 바꾸고 다른 값은 그대로 둔다")
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
    @DisplayName("changeNickname의 번호가 1보다 작으면 거부하고 닉네임과 번호를 바꾸지 않는다")
    void changeNicknameRejectsTagBelowOne(int tag) {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThatThrownBy(() -> user.changeNickname(new Nickname("영희"), tag))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getNickname()).isEqualTo("철수");
        assertThat(user.getTag()).isEqualTo(2);
    }

    // 같은 닉네임으로 바꾸면 번호를 새로 받지 않도록, 지금 닉네임과 같은지 판단한다

    @Test
    @DisplayName("지금과 같은 닉네임이면 hasNickname은 참이다")
    void hasSameNickname() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThat(user.hasNickname(new Nickname("철수"))).isTrue();
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 닉네임도 같은 닉네임이다")
    void hasSameNicknameAfterStrip() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThat(user.hasNickname(new Nickname("  철수 "))).isTrue();
    }

    @Test
    @DisplayName("다른 닉네임이면 hasNickname은 거짓이다")
    void doesNotHaveOtherNickname() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        assertThat(user.hasNickname(new Nickname("영희"))).isFalse();
    }

    @Test
    @DisplayName("대소문자만 다른 닉네임은 다른 닉네임이다")
    void caseDifferentNicknameIsOther() {
        User user = new User("user@example.com", "hash", new Nickname("Kim"), 2);

        assertThat(user.hasNickname(new Nickname("kim"))).isFalse();
        assertThat(user.hasNickname(new Nickname("Kim"))).isTrue();
    }

    @Test
    @DisplayName("hasNickname은 판단만 하고 닉네임과 번호를 바꾸지 않는다")
    void hasNicknameDoesNotChangeState() {
        User user = new User("user@example.com", "hash", NICKNAME, 2);

        user.hasNickname(new Nickname("영희"));

        assertThat(user.getNickname()).isEqualTo("철수");
        assertThat(user.getTag()).isEqualTo(2);
    }

    // 토큰 버전

    @Test
    @DisplayName("새로 만든 사용자의 토큰 버전은 새 회원의 버전 상수이고, 그 값은 0이다")
    void newUserHasInitialTokenVersion() {
        assertThat(User.INITIAL_TOKEN_VERSION).isZero();
        assertThat(new User("user@example.com", "hash", NICKNAME, 1).getTokenVersion())
                .isEqualTo(User.INITIAL_TOKEN_VERSION);
        assertThat(new User("admin@example.com", "hash", Role.ADMIN, NICKNAME, 1).getTokenVersion())
                .isEqualTo(User.INITIAL_TOKEN_VERSION);
    }

    @Test
    @DisplayName("revokeTokens를 부를 때마다 토큰 버전이 1씩 오르고 다른 값은 그대로다")
    void revokeTokensIncrementsVersion() {
        User user = withId(new User("user@example.com", "hash", Role.ADMIN, NICKNAME, 2), OTHER_ID);

        user.revokeTokens();
        assertThat(user.getTokenVersion()).isEqualTo(1);

        user.revokeTokens();
        assertThat(user.getTokenVersion()).isEqualTo(2);
        assertThat(user.getId()).isEqualTo(OTHER_ID);
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getNicknameTag()).isEqualTo(new NicknameTag("철수", 2));
    }

    @Test
    @DisplayName("tokenSubject는 회원 ID와 지금 토큰 버전을 담는다")
    void tokenSubjectHasIdAndCurrentVersion() {
        User user = withId(new User("user@example.com", "hash", NICKNAME, 1), OTHER_ID);

        assertThat(user.tokenSubject()).isEqualTo(new TokenSubject(OTHER_ID, User.INITIAL_TOKEN_VERSION));
    }

    @Test
    @DisplayName("revokeTokens 뒤의 tokenSubject는 오른 버전을 담아, 그 뒤 로그인한 토큰은 새 버전으로 발급된다")
    void tokenSubjectFollowsRevokedVersion() {
        User user = withId(new User("user@example.com", "hash", NICKNAME, 1), OTHER_ID);

        user.revokeTokens();

        assertThat(user.tokenSubject()).isEqualTo(new TokenSubject(OTHER_ID, 1));
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
