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
}
