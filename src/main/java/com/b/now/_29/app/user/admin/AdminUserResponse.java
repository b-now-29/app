package com.b.now._29.app.user.admin;

import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.User;
import java.time.Instant;
import java.util.List;

/** categorySlugs는 삭제되지 않은 담당 카테고리의 slug다 (카테고리 표시 순서). */
public record AdminUserResponse(
        Long id, String email, Role role, Instant createdAt, String nickname, int tag, List<String> categorySlugs) {

    public AdminUserResponse {
        categorySlugs = List.copyOf(categorySlugs);
    }

    public static AdminUserResponse from(User user, List<String> categorySlugs) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getNickname(),
                user.getTag(),
                categorySlugs);
    }
}
