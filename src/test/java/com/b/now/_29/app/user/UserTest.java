package com.b.now._29.app.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

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
}
