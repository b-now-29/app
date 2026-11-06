package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자 요청마다 DB에서 role을 확인한다 (D3).
 *
 * <p>구현할 API: {@code @Service UserRoleService(UserRepository)}, {@code boolean isAdmin(long userId)}. {@code
 * SecurityConfig}가 {@code /api/v1/admin/**} 인가에 이 빈을 쓴다. {@code @WebMvcTest}에서는 {@code @WithAppSecurity}가 mock으로
 * 넣는다.
 */
@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserRoleService userRoleService;

    @Test
    @DisplayName("role이 ADMIN인 사용자는 관리자다 (D3)")
    void adminIsAdmin() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(new User("a@example.com", "hash", Role.ADMIN)));

        assertThat(userRoleService.isAdmin(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("role이 USER인 사용자는 관리자가 아니다 (R2)")
    void userIsNotAdmin() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(new User("u@example.com", "hash")));

        assertThat(userRoleService.isAdmin(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("토큰은 유효하지만 DB에 없는 사용자는 관리자가 아니다")
    void unknownUserIsNotAdmin() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThat(userRoleService.isAdmin(USER_ID)).isFalse();
    }
}
