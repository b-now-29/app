package com.b.now._29.app.user.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 관리자 회원 항목 (docs/features/nickname.md R11, role-owner-category-admin API 계약 {@code categorySlugs}). */
class AdminUserResponseTest {

    @Test
    @DisplayName("회원의 id, email, role, createdAt, nickname, tag와 담당 카테고리 slug를 옮긴다")
    void fromUserIncludesNicknameTagAndCategorySlugs() {
        User user = new User("user@example.com", "hash", Role.CATEGORY_ADMIN, new Nickname("철수"), 4);
        ReflectionTestUtils.setField(user, "id", 9L);

        AdminUserResponse response = AdminUserResponse.from(user, List.of("java", "spring"));

        assertThat(response)
                .isEqualTo(new AdminUserResponse(
                        9L,
                        "user@example.com",
                        Role.CATEGORY_ADMIN,
                        user.getCreatedAt(),
                        "철수",
                        4,
                        List.of("java", "spring")));
    }

    @Test
    @DisplayName("담당 카테고리 목록은 불변으로 담아, 넘긴 목록을 나중에 바꿔도 응답은 그대로다")
    void categorySlugsAreImmutableCopy() {
        User user = new User("user@example.com", "hash", Role.CATEGORY_ADMIN, new Nickname("철수"), 4);
        List<String> slugs = new ArrayList<>(List.of("java"));

        AdminUserResponse response = AdminUserResponse.from(user, slugs);
        slugs.add("spring");

        assertThat(response.categorySlugs()).containsExactly("java");
        assertThatThrownBy(() -> response.categorySlugs().add("kotlin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
