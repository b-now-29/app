package com.b.now._29.app.user.admin;

import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import java.time.Instant;

public record AdminUserResponse(Long id, String email, Role role, Instant createdAt, String nickname, int tag) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt(), user.getNickname(), user.getTag());
    }
}
