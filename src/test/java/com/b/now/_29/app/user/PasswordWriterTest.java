package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 비밀번호 변경의 저장 단계 (docs/features/password-change.md R1, R4, D3, D4, D5). 비교와 해시는 트랜잭션 밖의 {@code UserService}가
 * 끝냈고, 비교에 쓴 해시가 그대로인지 확인하고 바꾸는 규칙은 {@code User.changePassword}에 있다 ({@code UserTest}). 여기서는 회원을 찾아 그
 * 결과(새 토큰 주체·예외)가 그대로 전달되는지만 확인한다.
 *
 * <p>트랜잭션 동작은 단위 테스트로 확인되지 않으므로 {@code PasswordChangeIntegrationTest}가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PasswordWriterTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String OLD_HASH = "$2a$10$old-hash";
    private static final String NEW_HASH = "$2a$10$new-hash";
    private static final Nickname NICKNAME = new Nickname("철수");

    @Mock
    UserRepository userRepository;

    PasswordWriter writer;

    @BeforeEach
    void setUp() {
        writer = new PasswordWriter(userRepository);
    }

    @Test
    @DisplayName("지금 해시가 비교에 쓴 해시와 같으면 새 해시로 바꾸고 토큰 버전을 올린 뒤, 새 버전의 토큰 주체를 돌려준다 (R1, R4, R5)")
    void changesHashAndReturnsNewTokenSubject() {
        User user = withId(new User(EMAIL, OLD_HASH, NICKNAME, 1));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        TokenSubject subject = writer.changePassword(USER_ID, OLD_HASH, NEW_HASH);

        assertThat(user.getPasswordHash()).isEqualTo(NEW_HASH);
        assertThat(user.getTokenVersion()).isEqualTo(User.INITIAL_TOKEN_VERSION + 1);
        assertThat(subject).isEqualTo(new TokenSubject(USER_ID, User.INITIAL_TOKEN_VERSION + 1));
    }

    @Test
    @DisplayName("엔티티 메서드로 바꾸고 merge하지 않는다: save·saveAndFlush를 부르지 않는다 (D4, @DynamicUpdate)")
    void doesNotMerge() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(withId(new User(EMAIL, OLD_HASH, NICKNAME, 1))));

        writer.changePassword(USER_ID, OLD_HASH, NEW_HASH);

        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("비교와 저장 사이에 다른 요청이 비밀번호를 바꿨으면 엔티티의 R2 예외가 그대로 나오고 해시와 토큰 버전은 그대로다 (D4, D5)")
    void rejectsWhenHashChangedSinceComparison() {
        String changedByOther = "$2a$10$changed-by-other-request";
        User user = withId(new User(EMAIL, changedByOther, NICKNAME, 1));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> writer.changePassword(USER_ID, OLD_HASH, NEW_HASH))
                .isInstanceOf(WrongCurrentPasswordException.class)
                .hasMessage("현재 비밀번호가 맞지 않습니다.");

        assertThat(user.getPasswordHash()).isEqualTo(changedByOther);
        assertThat(user.getTokenVersion()).isEqualTo(User.INITIAL_TOKEN_VERSION);
    }

    @Test
    @DisplayName("없는 회원이면 UserNotFoundException이다")
    void unknownUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> writer.changePassword(USER_ID, OLD_HASH, NEW_HASH))
                .isInstanceOf(UserNotFoundException.class);
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
