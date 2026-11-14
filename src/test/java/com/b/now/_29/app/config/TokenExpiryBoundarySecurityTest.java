package com.b.now._29.app.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.UserController;
import com.b.now._29.app.user.UserResponse;
import com.b.now._29.app.user.UserService;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code exp} 경계를 실제 보안 필터로 확인한다 (token-sliding-expiry R12, D14).
 *
 * <p>요청 중에 실제 시계가 흘러가면 "exp가 정확히 지금"을 만들 수 없으므로 {@code Clock} 빈을 고정 시계로 바꾼다.
 */
@WebMvcTest(UserController.class)
@WithAppSecurity
class TokenExpiryBoundarySecurityTest {

    private static final long USER_ID = 1L;
    private static final String ME = "/api/v1/users/me";
    private static final Instant NOW = Instant.parse("2030-03-15T09:30:00Z");

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @Test
    @DisplayName("exp가 정확히 지금인 쿠키로 /users/me를 부르면 401이고 새 쿠키를 주지 않는다 (R12)")
    void tokenExpiringNowIsGuest() throws Exception {
        given(userService.getUser(USER_ID))
                .willReturn(new UserResponse(USER_ID, "user@example.com", Role.USER, "철수", 1));
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)), NOW);

        mvc.perform(get(ME).cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("exp가 1초 남은 쿠키는 200이고 새 쿠키를 받는다 (R12의 바로 앞 경계, 고정 시계가 쓰인다는 확인)")
    void tokenExpiringInOneSecondIsRenewed() throws Exception {
        given(userService.getUser(USER_ID))
                .willReturn(new UserResponse(USER_ID, "user@example.com", Role.USER, "철수", 1));
        String token = TestJwts.of(USER_ID, NOW.minus(Duration.ofHours(1)).plusSeconds(1), NOW.plusSeconds(1));

        mvc.perform(get(ME).cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }
}
