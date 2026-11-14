package com.b.now._29.app.user;

public record UserResponse(long id, String email, Role role, String nickname, int tag) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getNickname(), user.getTag());
    }
}
