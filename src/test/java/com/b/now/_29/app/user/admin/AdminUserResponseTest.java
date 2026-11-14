package com.b.now._29.app.user.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 관리자 회원 목록 항목에 닉네임과 번호를 담는다 (docs/features/nickname.md R11). */
class AdminUserResponseTest {

    @Test
    @DisplayName("회원의 id, email, role, createdAt과 함께 nickname, tag를 옮긴다 (nickname R11)")
    void fromUserIncludesNicknameAndTag() {
        User user = new User("user@example.com", "hash", Role.ADMIN, new Nickname("철수"), 4);
        ReflectionTestUtils.setField(user, "id", 9L);

        AdminUserResponse response = AdminUserResponse.from(user);

        assertThat(response)
                .isEqualTo(new AdminUserResponse(9L, "user@example.com", Role.ADMIN, user.getCreatedAt(), "철수", 4));
    }
}
