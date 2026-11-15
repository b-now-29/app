package com.b.now._29.app.support;

import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트에서 운영자 (OWNER)로 요청한다.
 *
 * <p>OWNER는 DB 전체에서 정확히 1명이라 (role-owner-category-admin R2, D5) 테스트마다 새로 만들 수 없다. 같은 DB를 쓰는 테스트들이 이미 있는
 * OWNER를 함께 쓰고, 없을 때만 만든다.
 */
public final class TestOwner {

    private static final String COOKIE_NAME = "access_token";
    private static final String PASSWORD = "owner1234!";

    private TestOwner() {}

    /** OWNER의 회원 id. 없으면 {@code UserService.createAccount}로 만든다. */
    public static long id(JdbcTemplate jdbc, UserService userService) {
        List<Long> owners = jdbc.queryForList("SELECT id FROM users WHERE role = 'OWNER'", Long.class);
        if (!owners.isEmpty()) {
            return owners.getFirst();
        }
        String email = "owner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return userService.createAccount(email, PASSWORD, "운영자", Role.OWNER).id();
    }

    /** OWNER의 지금 토큰 버전으로 발급한 인증 쿠키. 다른 테스트가 토큰 버전을 올렸어도 유효하다. */
    public static Cookie cookie(JdbcTemplate jdbc, UserService userService) {
        long ownerId = id(jdbc, userService);
        Integer version = jdbc.queryForObject("SELECT token_version FROM users WHERE id = ?", Integer.class, ownerId);
        return new Cookie(COOKIE_NAME, TestJwts.withVersion(ownerId, version == null ? 0 : version));
    }
}
