package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자 요청마다 DB에서 role을 확인한다 (D3). 보안 계층과 랭킹이 함께 쓴다 (role-owner-category-admin API 계약).
 *
 * <p>{@code @WebMvcTest}에서는 {@code @WithAppSecurity}가 mock으로 넣는다.
 */
@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserRoleService userRoleService;

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("회원의 role을 그대로 돌려준다")
    void findsRoleOfUser(Role role) {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(new User("a@example.com", "hash", role, new Nickname("회원"), 1)));

        assertThat(userRoleService.findRole(USER_ID)).contains(role);
    }

    @Test
    @DisplayName("토큰은 유효하지만 DB에 없는 사용자는 role이 없다 (empty)")
    void unknownUserHasNoRole() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThat(userRoleService.findRole(USER_ID)).isEmpty();
    }
}
