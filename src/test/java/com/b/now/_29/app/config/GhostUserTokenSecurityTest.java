package com.b.now._29.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.controller.PingController;
import com.b.now._29.app.quiz.admin.AdminCategoryController;
import com.b.now._29.app.quiz.admin.AdminCategoryService;
import com.b.now._29.app.quiz.attempt.QuestionAttemptController;
import com.b.now._29.app.quiz.attempt.QuestionAttemptService;
import com.b.now._29.app.quiz.question.QuestionController;
import com.b.now._29.app.quiz.question.QuestionService;
import com.b.now._29.app.quiz.ranking.RankingController;
import com.b.now._29.app.quiz.ranking.RankingService;
import com.b.now._29.app.support.SetCookieHeader;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import com.b.now._29.app.user.Role;
import com.b.now._29.app.user.TokenUserVerifier;
import com.b.now._29.app.user.UserController;
import com.b.now._29.app.user.UserResponse;
import com.b.now._29.app.user.UserRoleService;
import com.b.now._29.app.user.UserService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 토큰은 유효한데 회원이 DB에 없으면 그 요청은 비회원으로 처리되고 토큰도 연장되지 않는다. 회원이 있는 토큰의 인증·연장·관리자 판정은 그대로다.
 *
 * <p>회원 존재 여부는 {@code @WithAppSecurity}가 넣는 {@code TokenUserVerifier} mock으로 정한다.
 */
@WebMvcTest({
    PingController.class,
    UserController.class,
    QuestionController.class,
    QuestionAttemptController.class,
    RankingController.class,
    AdminCategoryController.class
})
@WithAppSecurity
class GhostUserTokenSecurityTest {

    private static final long GHOST_ID = 404L;
    private static final long MEMBER_ID = 1L;
    private static final String ME = "/api/v1/users/me";
    private static final String COOKIE_NAME = "access_token";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @Autowired
    UserRoleService userRoleService;

    @Autowired
    TokenUserVerifier tokenUserVerifier;

    @MockitoBean
    QuestionService questionService;

    @MockitoBean
    QuestionAttemptService questionAttemptService;

    @MockitoBean
    RankingService rankingService;

    @MockitoBean
    AdminCategoryService adminCategoryService;

    @BeforeEach
    void setUp() {
        given(tokenUserVerifier.exists(GHOST_ID)).willReturn(false);
        given(tokenUserVerifier.exists(MEMBER_ID)).willReturn(true);
    }

    // 회원이 없는 토큰

    @Test
    @DisplayName("회원이 없는 토큰으로 /users/me를 부르면 401이고 회원 조회까지 가지 않는다")
    void meOfGhostIsUnauthorized() throws Exception {
        expectUnauthorized(mvc.perform(get(ME).cookie(cookie(TestJwts.valid(GHOST_ID)))));

        then(tokenUserVerifier).should().exists(GHOST_ID);
        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원이 없는 토큰으로 /users/me/attempts를 부르면 401이다")
    void attemptsOfGhostIsUnauthorized() throws Exception {
        expectUnauthorized(mvc.perform(get(ME + "/attempts").cookie(cookie(TestJwts.valid(GHOST_ID)))));

        then(questionAttemptService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원이 없는 토큰으로 자기 채점을 저장하면 401이다")
    void selfGradeOfGhostIsUnauthorized() throws Exception {
        expectUnauthorized(mvc.perform(post("/api/v1/questions/{id}/self-grades", 1L)
                .cookie(cookie(TestJwts.valid(GHOST_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correct\": true}")));

        then(questionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원이 없는 토큰으로 관리자 API를 부르면 403이 아니라 401이고 role을 확인하지 않는다")
    void adminOfGhostIsUnauthorized() throws Exception {
        given(userRoleService.isAdmin(anyLong())).willReturn(true);

        expectUnauthorized(mvc.perform(get("/api/v1/admin/categories").cookie(cookie(TestJwts.valid(GHOST_ID)))));

        then(adminCategoryService).shouldHaveNoInteractions();
        then(userRoleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원이 없는 토큰으로도 공개 API는 200이다")
    void publicApiOfGhostIsOk() throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(cookie(TestJwts.valid(GHOST_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("회원이 없는 토큰으로 답을 내면 비회원으로 채점한다: Service에 userId null이 간다")
    void answerOfGhostIsGradedAsGuest() throws Exception {
        mvc.perform(post("/api/v1/questions/{id}/answers", 1L)
                .cookie(cookie(TestJwts.valid(GHOST_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"choiceId\": 2}"));

        then(questionService).should().submitAnswer(eq(1L), eq(2L), isNull());
    }

    @Test
    @DisplayName("회원이 없는 토큰으로 랭킹을 보면 비회원으로 조회한다: Service에 userId null이 간다")
    void rankingOfGhostIsGuest() throws Exception {
        mvc.perform(get("/api/v1/rankings").cookie(cookie(TestJwts.valid(GHOST_ID))));
        mvc.perform(get("/api/v1/categories/{slug}/rankings", "java").cookie(cookie(TestJwts.valid(GHOST_ID))));

        then(rankingService).should().getRanking(isNull());
        then(rankingService).should().getCategoryRanking(any(), isNull());
    }

    // 연장

    @Test
    @DisplayName("회원이 없고 남은 시간이 30분 미만인 토큰이면 공개 API도 Set-Cookie가 없다")
    void nearExpiryGhostTokenIsNotRenewedOnPublicApi() throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(cookie(nearExpiry(GHOST_ID))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("회원이 없고 남은 시간이 30분 미만인 토큰으로 /users/me를 부르면 401이고 Set-Cookie가 없다")
    void nearExpiryGhostTokenIsNotRenewedOnMe() throws Exception {
        expectUnauthorized(mvc.perform(get(ME).cookie(cookie(nearExpiry(GHOST_ID)))))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // 회원이 있는 토큰은 그대로

    @Test
    @DisplayName("회원이 있는 토큰이면 /users/me는 200이고, 토큰의 sub로 회원 존재를 확인한다")
    void memberIsAuthenticated() throws Exception {
        given(userService.getUser(MEMBER_ID))
                .willReturn(new UserResponse(MEMBER_ID, "user@example.com", Role.USER, "철수", 1));

        mvc.perform(get(ME).cookie(cookie(TestJwts.valid(MEMBER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER_ID));

        then(tokenUserVerifier).should().exists(MEMBER_ID);
    }

    @Test
    @DisplayName("회원이 있고 남은 시간이 30분 미만인 토큰이면 공개 API에서 새 쿠키를 받는다")
    void nearExpiryMemberTokenIsRenewed() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/ping").cookie(cookie(nearExpiry(MEMBER_ID))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        assertThat(SetCookieHeader.from(result).name()).isEqualTo(COOKIE_NAME);
    }

    @Test
    @DisplayName("회원이 있는 토큰으로 답을 내면 Service에 그 회원 id가 간다")
    void answerOfMemberIsRecordedAsMember() throws Exception {
        mvc.perform(post("/api/v1/questions/{id}/answers", 1L)
                .cookie(cookie(TestJwts.valid(MEMBER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"choiceId\": 2}"));

        then(questionService).should().submitAnswer(1L, 2L, MEMBER_ID);
    }

    @Test
    @DisplayName("회원이 있는 관리자 토큰은 관리자 API 인가를 통과하고, USER면 403이다")
    void adminDecisionOfMemberIsUnchanged() throws Exception {
        given(adminCategoryService.getCategories()).willReturn(List.of());
        given(userRoleService.isAdmin(MEMBER_ID)).willReturn(true, false);
        Cookie member = cookie(TestJwts.valid(MEMBER_ID));

        mvc.perform(get("/api/v1/admin/categories").cookie(member)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories").cookie(member)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("만료된 토큰이나 토큰 없는 요청이면 회원 존재를 조회하지 않는다")
    void invalidTokenDoesNotQueryUser() throws Exception {
        mvc.perform(get("/api/v1/ping").cookie(cookie(TestJwts.expired(MEMBER_ID))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());

        then(tokenUserVerifier).should(never()).exists(anyLong());
    }

    /** 남은 시간이 10분인 정상 토큰 (연장 대상). */
    private static String nearExpiry(long userId) {
        Instant now = Instant.now();
        return TestJwts.of(userId, now.minus(Duration.ofMinutes(50)), now.plus(Duration.ofMinutes(10)));
    }

    private static Cookie cookie(String token) {
        return new Cookie(COOKIE_NAME, token);
    }

    private static ResultActions expectUnauthorized(ResultActions actions) throws Exception {
        return actions.andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
