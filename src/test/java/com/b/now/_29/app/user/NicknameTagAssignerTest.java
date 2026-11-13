package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 번호를 정해 회원을 저장하는 "한 번 시도" (docs/features/nickname.md R3, R5, D3, D11, D12)에서 추가로 구현할 것:
 *
 * <ul>
 *   <li>{@code @Service class NicknameTagAssigner(UserRepository)} — 메서드 하나가 번호 경합 재시도의 한 번 시도다. 각 메서드는 자기
 *       {@code @Transactional}로 돌고, 제약 위반 ({@code DataIntegrityViolationException})은 그대로 던진다. 재시도와 예외 구분은
 *       {@code UserService}가 한다
 *   <li>{@code UserResponse createUser(String email, String passwordHash, Role role, Nickname nickname)} — 번호는 {@code
 *       NicknameTag.nextTag}/{@code FIRST_TAG}로 정하고 {@code saveAndFlush}로 저장한다 (제약 위반이 이 메서드 안에서 나도록)
 *   <li>{@code UserResponse changeNickname(long userId, Nickname nickname)} — 없는 회원은 {@code UserNotFoundException}. {@code
 *       user.hasNickname}이면 아무것도 바꾸지 않는다. 아니면 {@code user.changeNickname(nickname, 번호)} 후 {@code
 *       userRepository.flush()}. {@code save}·{@code saveAndFlush}로 merge하지 않는다 (lost update, D12)
 * </ul>
 *
 * <p>트랜잭션 동작은 단위 테스트로 확인되지 않으므로 {@code NicknameTagRetryIntegrationTest}가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class NicknameTagAssignerTest {

    private static final long USER_ID = 1L;
    private static final String EMAIL = "user@example.com";
    private static final String HASH = "$2a$10$hash";
    private static final Nickname NICKNAME = new Nickname("철수");

    @Mock
    UserRepository userRepository;

    NicknameTagAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new NicknameTagAssigner(userRepository);
    }

    // 가입 한 번 시도

    @Test
    @DisplayName("그 닉네임의 가장 큰 번호가 3이면 번호 4로 저장하고 응답한다 (R3, D3)")
    void createUserTakesMaxPlusOne() {
        given(userRepository.findMaxTagByNickname("철수")).willReturn(Optional.of(3));
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> withId(invocation.getArgument(0)));

        UserResponse response = assigner.createUser(EMAIL, HASH, Role.ADMIN, NICKNAME);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(HASH);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getNicknameTag()).isEqualTo(new NicknameTag("철수", 4));
        assertThat(response).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.ADMIN, "철수", 4));
    }

    @Test
    @DisplayName("그 닉네임을 쓰는 회원이 없으면 번호 1로 저장한다 (R3)")
    void createUserTakesFirstTag() {
        given(userRepository.findMaxTagByNickname("철수")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> withId(invocation.getArgument(0)));

        assertThat(assigner.createUser(EMAIL, HASH, Role.USER, NICKNAME).tag()).isEqualTo(1);
    }

    // 닉네임 변경 한 번 시도

    @Test
    @DisplayName("다른 닉네임으로 바꾸면 새 닉네임의 가장 큰 번호 + 1로 엔티티를 바꾸고 flush한다. merge하지 않는다 (R3, R5, D12)")
    void changeNicknameToOther() {
        User user = withId(new User(EMAIL, HASH, Role.ADMIN, NICKNAME, 2));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRepository.findMaxTagByNickname("영희")).willReturn(Optional.of(4));

        UserResponse response = assigner.changeNickname(USER_ID, new Nickname("영희"));

        assertThat(response).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.ADMIN, "영희", 5));
        assertThat(user.getNicknameTag()).isEqualTo(new NicknameTag("영희", 5));
        then(userRepository).should().flush();
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("새 닉네임을 쓰는 회원이 없으면 번호 1을 받는다 (R3)")
    void changeNicknameToUnused() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(withId(new User(EMAIL, HASH, NICKNAME, 2))));
        given(userRepository.findMaxTagByNickname("영희")).willReturn(Optional.empty());

        assertThat(assigner.changeNickname(USER_ID, new Nickname("영희")).tag()).isEqualTo(1);
    }

    @Test
    @DisplayName("지금과 같은 닉네임이면 번호를 계산하지 않고 아무것도 바꾸지 않는다 (R5, D11)")
    void changeNicknameToSame() {
        User user = withId(new User(EMAIL, HASH, NICKNAME, 2));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        UserResponse response = assigner.changeNickname(USER_ID, new Nickname(" 철수 "));

        assertThat(response).isEqualTo(new UserResponse(USER_ID, EMAIL, Role.USER, "철수", 2));
        assertThat(user.getNicknameTag()).isEqualTo(new NicknameTag("철수", 2));
        then(userRepository).should(never()).findMaxTagByNickname(anyString());
        then(userRepository).should(never()).save(any(User.class));
        then(userRepository).should(never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("없는 회원이면 UserNotFoundException이다")
    void changeNicknameOfUnknownUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());
        lenient().when(userRepository.findMaxTagByNickname("영희")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assigner.changeNickname(USER_ID, new Nickname("영희")))
                .isInstanceOf(UserNotFoundException.class);
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
