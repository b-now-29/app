package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.TestcontainersConfiguration;
import com.b.now._29.app.support.TestJwts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 번호 경합 재시도를 실제 트랜잭션과 DB 제약으로 확인한다 (docs/features/nickname.md R4, D4, 제약 사항).
 *
 * <p>동시 요청은 재현이 불확실하므로, {@code findMaxTagByNickname}이 경쟁자가 저장하기 전의 오래된 값을 돌려주게 해 실제 {@code
 * uk_users_nickname_tag} 위반을 일으킨다. 재시도가 같은 (rollback-only) 트랜잭션 안에서 일어나면 이 테스트는 실패한다.
 *
 * <p>(D12) 한 번 시도는 {@code NicknameTagAssigner}의 {@code @Transactional} 메서드, 재시도 반복은 {@code UserService}의
 * {@code NOT_SUPPORTED} 입구가 한다. 이 테스트는 {@code UserService}를 통해 그 조합을 확인한다.
 */
@Tag("integration")
@SpringBootTest(properties = "JWT_SECRET=" + TestJwts.SECRET)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NicknameTagRetryIntegrationTest {

    private static final String PASSWORD = "password1234";

    @MockitoSpyBean
    UserRepository userRepository;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("가입 중 번호가 겹치면 트랜잭션 밖에서 번호를 다시 계산해 다음 번호로 가입된다 (R4, D4)")
    void signUpRetriesAfterRealConflict() {
        String nickname = uniqueNickname();
        UserResponse first = userService.signUp(uniqueEmail(), PASSWORD, nickname);
        AtomicInteger calls = staleOnFirstCall(nickname);

        UserResponse second = userService.signUp(uniqueEmail(), PASSWORD, nickname);

        assertThat(first.tag()).isEqualTo(1);
        assertThat(second.tag()).isEqualTo(2);
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
        assertThat(tagsOf(nickname)).containsExactly(1, 2);
        assertThat(userRepository.findById(second.id())).hasValueSatisfying(saved -> {
            assertThat(saved.getNickname()).isEqualTo(nickname);
            assertThat(saved.getTag()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("닉네임 변경 중 번호가 겹치면 다시 계산해 다음 번호로 바뀐다 (R4, R5, D4)")
    void changeNicknameRetriesAfterRealConflict() {
        String nickname = uniqueNickname();
        userService.signUp(uniqueEmail(), PASSWORD, nickname);
        UserResponse other = userService.signUp(uniqueEmail(), PASSWORD, uniqueNickname());
        AtomicInteger calls = staleOnFirstCall(nickname);

        UserResponse changed = userService.changeNickname(other.id(), nickname);

        assertThat(changed.nickname()).isEqualTo(nickname);
        assertThat(changed.tag()).isEqualTo(2);
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
        assertThat(tagsOf(nickname)).containsExactly(1, 2);
    }

    @Test
    @DisplayName("닉네임을 바꾸는 사이 다른 경로로 바뀐 role을 옛 값으로 덮어쓰지 않는다 (제약 사항: lost update)")
    void changeNicknameKeepsRoleChangedMeanwhile() {
        UserResponse member = userService.signUp(uniqueEmail(), PASSWORD, uniqueNickname());
        String newNickname = uniqueNickname();
        // 번호를 계산하는 순간 관리자가 이 회원을 ADMIN으로 바꾼 상황. 구현이 회원을 언제 읽든 번호 계산은 반드시 거친다.
        // D12 구조에서는 한 번 시도가 회원을 먼저 읽고 (hasNickname) 번호를 계산하므로, 영속 상태의 role은 옛 값 USER다.
        // @DynamicUpdate가 없으면 엔티티 저장이 role까지 UPDATE해 이 테스트가 실패한다
        given(userRepository.findMaxTagByNickname(newNickname)).willAnswer(invocation -> {
            jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", member.id());
            return Optional.empty();
        });

        UserResponse changed = userService.changeNickname(member.id(), newNickname);

        assertThat(changed.nickname()).isEqualTo(newNickname);
        assertThat(changed.tag()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, member.id()))
                .isEqualTo("ADMIN");
        assertThat(jdbc.queryForMap("SELECT nickname, tag FROM users WHERE id = ?", member.id()))
                .containsEntry("nickname", newNickname)
                .containsEntry("tag", 1);
    }

    @Test
    @DisplayName("같은 닉네임으로 바꾸면 번호를 유지하고, 가입 뒤 바뀐 role도 그대로다 (R5, 제약 사항)")
    void changeToSameNicknameKeepsRoleChangedMeanwhile() {
        String nickname = uniqueNickname();
        UserResponse member = userService.signUp(uniqueEmail(), PASSWORD, nickname);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", member.id());

        UserResponse unchanged = userService.changeNickname(member.id(), nickname);

        assertThat(unchanged.nickname()).isEqualTo(nickname);
        assertThat(unchanged.tag()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, member.id()))
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("번호 경합이 계속되면 재시도를 멈추고 가입은 500이며 회원이 생기지 않는다 (D4)")
    void signUpGivesUpWith500() throws Exception {
        String nickname = uniqueNickname();
        userService.signUp(uniqueEmail(), PASSWORD, nickname);
        given(userRepository.findMaxTagByNickname(nickname)).willReturn(Optional.empty());
        String email = uniqueEmail();

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, nickname)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(userRepository.existsByEmail(email)).isFalse();
        assertThat(tagsOf(nickname)).containsExactly(1);
    }

    @Test
    @DisplayName("닉네임 변경도 번호 경합이 계속되면 멈추고 원래 닉네임과 번호가 그대로다 (D4)")
    void changeNicknameGivesUp() {
        String nickname = uniqueNickname();
        userService.signUp(uniqueEmail(), PASSWORD, nickname);
        String originalNickname = uniqueNickname();
        UserResponse other = userService.signUp(uniqueEmail(), PASSWORD, originalNickname);
        given(userRepository.findMaxTagByNickname(nickname)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeNickname(other.id(), nickname))
                .isNotInstanceOf(InvalidNicknameException.class);

        assertThat(userRepository.findById(other.id())).hasValueSatisfying(saved -> {
            assertThat(saved.getNickname()).isEqualTo(originalNickname);
            assertThat(saved.getTag()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("가입 경합이 이메일 제약 위반이면 번호 경합으로 재시도하지 않고 409다 (제약 사항)")
    void emailRaceIsConflictNotRetry() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();
        userService.signUp(email, PASSWORD, nickname);
        // 사전 중복 검사를 통과한 뒤 다른 요청이 같은 이메일을 저장한 상황
        given(userRepository.existsByEmail(email)).willReturn(false);

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, uniqueNickname())))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("이메일과 번호가 모두 겹치지 않는 같은 닉네임 가입은 409가 아니라 다음 번호로 201이다 (R2, 제약 사항)")
    void sameNicknameDifferentEmailIsCreated() throws Exception {
        String nickname = uniqueNickname();
        userService.signUp(uniqueEmail(), PASSWORD, nickname);
        staleOnFirstCall(nickname);

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(uniqueEmail(), nickname)))
                .andExpect(status().isCreated());

        assertThat(tagsOf(nickname)).containsExactly(1, 2);
    }

    /** 첫 호출은 경쟁자가 저장하기 전 값 (empty → 번호 1), 그다음부터는 DB의 실제 최댓값을 돌려준다. */
    private AtomicInteger staleOnFirstCall(String nickname) {
        AtomicInteger calls = new AtomicInteger();
        given(userRepository.findMaxTagByNickname(nickname)).willAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(
                    jdbc.queryForObject("SELECT max(tag) FROM users WHERE nickname = ?", Integer.class, nickname));
        });
        return calls;
    }

    private List<Integer> tagsOf(String nickname) {
        return jdbc.queryForList("SELECT tag FROM users WHERE nickname = ? ORDER BY tag", Integer.class, nickname);
    }

    private static String signUpBody(String email, String nickname) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"%s\"}".formatted(email, PASSWORD, nickname);
    }

    private static String uniqueNickname() {
        return "경합" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueEmail() {
        return "retry-" + UUID.randomUUID() + "@example.com";
    }
}
