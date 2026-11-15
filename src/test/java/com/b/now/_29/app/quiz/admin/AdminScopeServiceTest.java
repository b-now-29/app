package com.b.now._29.app.quiz.admin;

import static com.b.now._29.app.support.TestCategories.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import com.b.now._29.app.user.UserRoleService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 요청한 관리자의 역할과 담당 카테고리로 {@link AdminScope}를 만든다 (role-owner-category-admin API 계약 {@code
 * AdminScopeService.of}).
 *
 * <p>의존: {@code UserRoleService} (역할), {@code CategoryAdminRepository} (담당 카테고리).
 */
@ExtendWith(MockitoExtension.class)
class AdminScopeServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    UserRoleService userRoleService;

    @Mock
    CategoryAdminRepository categoryAdminRepository;

    @InjectMocks
    AdminScopeService adminScopeService;

    @Test
    @DisplayName("OWNER면 OWNER 범위다 (R4)")
    void ownerScope() {
        given(userRoleService.findRole(USER_ID)).willReturn(Optional.of(Role.OWNER));

        assertThat(adminScopeService.of(USER_ID)).isEqualTo(AdminScope.owner());
    }

    @Test
    @DisplayName("CATEGORY_ADMIN이면 담당 카테고리 id로 범위를 만든다 (R4)")
    void categoryAdminScope() {
        given(userRoleService.findRole(USER_ID)).willReturn(Optional.of(Role.CATEGORY_ADMIN));
        given(categoryAdminRepository.findAllByUserId(USER_ID))
                .willReturn(List.of(
                        new CategoryAdmin(manager(), withId(1L, "java")),
                        new CategoryAdmin(manager(), withId(3L, "spring"))));

        AdminScope scope = adminScopeService.of(USER_ID);

        assertThat(scope).isEqualTo(AdminScope.categories(Set.of(1L, 3L)));
        assertThat(scope.isOwner()).isFalse();
        assertThat(scope.canManage(withId(1L, "java"))).isTrue();
        assertThat(scope.canManage(withId(2L, "kotlin"))).isFalse();
    }

    @Test
    @DisplayName("USER면 아무 카테고리도 관리하지 않는 범위다")
    void userScopeIsEmpty() {
        given(userRoleService.findRole(USER_ID)).willReturn(Optional.of(Role.USER));

        AdminScope scope = adminScopeService.of(USER_ID);

        assertThat(scope.isOwner()).isFalse();
        assertThat(scope.canManage(withId(1L, "java"))).isFalse();
    }

    private static User manager() {
        User user = new User("manager@example.com", "hash", Role.CATEGORY_ADMIN, new Nickname("매니저"), 1);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
