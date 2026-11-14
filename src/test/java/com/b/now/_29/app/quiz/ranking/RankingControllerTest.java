package com.b.now._29.app.quiz.ranking;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.cookies.CookieDocumentation.requestCookies;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.support.TestJwts;
import com.b.now._29.app.support.WithAppSecurity;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 구현할 API: {@code RankingController}
 *
 * <ul>
 *   <li>{@code GET /api/v1/rankings} → {@code rankingService.getRanking(Long userId)}
 *   <li>{@code GET /api/v1/categories/{slug}/rankings} → {@code rankingService.getCategoryRanking(String slug, Long userId)}
 * </ul>
 *
 * <p>공개 API다 (R8). userId는 {@code @AuthenticationPrincipal Long}이고 비회원·잘못된 토큰이면 null이다. 관리자와 점수 0 회원의 me는 Service가
 * 정한다 (RankingServiceTest).
 */
@WebMvcTest(RankingController.class)
@AutoConfigureRestDocs
@WithAppSecurity
class RankingControllerTest {

    private static final long USER_ID = 7L;
    private static final String COOKIE_NAME = "access_token";
    private static final String SLUG = "java";
    private static final String RANKINGS = "/api/v1/rankings";
    private static final String CATEGORY_RANKINGS = "/api/v1/categories/{slug}/rankings";

    private static final List<RankingEntryResponse> ENTRIES =
            List.of(new RankingEntryResponse(1L, "철수", 3, 42L), new RankingEntryResponse(2L, "영희", 1, 40L));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RankingService rankingService;

    // 전체 랭킹

    @Test
    @DisplayName("로그인한 회원은 상위 목록과 me(자기 등수와 점수)를 받는다. 줄에는 등수, 닉네임, 번호, 점수만 있다 (R6, R7)")
    void getRankingOfMember() throws Exception {
        given(rankingService.getRanking(USER_ID))
                .willReturn(new RankingResponse(ENTRIES, new MyRankingResponse(77L, 5L)));

        mvc.perform(get(RANKINGS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("entries", "me")))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].keys()", containsInAnyOrder("rank", "nickname", "tag", "score")))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].nickname").value("철수"))
                .andExpect(jsonPath("$.entries[0].tag").value(3))
                .andExpect(jsonPath("$.entries[0].score").value(42))
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.entries[1].nickname").value("영희"))
                .andExpect(jsonPath("$.me.keys()", containsInAnyOrder("rank", "score")))
                .andExpect(jsonPath("$.me.rank").value(77))
                .andExpect(jsonPath("$.me.score").value(5))
                .andExpect(noPersonalIdentifiers())
                .andDo(document(
                        "rankings",
                        requestCookies(cookieWithName(COOKIE_NAME)
                                .optional()
                                .description("로그인할 때 받은 인증 쿠키 (없으면 me 없이 목록만 받는다)")),
                        responseFields(rankingFields())));

        then(rankingService).should().getRanking(USER_ID);
    }

    @Test
    @DisplayName("비회원도 랭킹을 볼 수 있고 me는 null이다 (R8, D6)")
    void getRankingOfGuest() throws Exception {
        given(rankingService.getRanking(null)).willReturn(new RankingResponse(ENTRIES, null));

        mvc.perform(get(RANKINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("entries", "me")))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.me").value(nullValue()))
                .andExpect(noPersonalIdentifiers())
                .andDo(document("rankings-guest"));

        then(rankingService).should().getRanking(null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("토큰이 잘못되면 거부하지 않고 비회원으로 조회해 me는 null이다 (R8)")
    void treatsInvalidTokenAsGuest(String name, String token) throws Exception {
        given(rankingService.getRanking(null)).willReturn(new RankingResponse(ENTRIES, null));

        mvc.perform(get(RANKINGS).cookie(new Cookie(COOKIE_NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(nullValue()));

        then(rankingService).should().getRanking(null);
    }

    @Test
    @DisplayName("관리자는 Service가 me를 null로 주고 응답에도 null로 나간다 (R3, D4)")
    void getRankingOfAdmin() throws Exception {
        given(rankingService.getRanking(USER_ID)).willReturn(new RankingResponse(ENTRIES, null));

        mvc.perform(get(RANKINGS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(nullValue()));
    }

    @Test
    @DisplayName("점수가 0인 회원의 me는 rank null, score 0이다 (R7)")
    void getRankingOfMemberWithoutScore() throws Exception {
        given(rankingService.getRanking(USER_ID))
                .willReturn(new RankingResponse(ENTRIES, new MyRankingResponse(null, 0L)));

        mvc.perform(get(RANKINGS).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.keys()", containsInAnyOrder("rank", "score")))
                .andExpect(jsonPath("$.me.rank").value(nullValue()))
                .andExpect(jsonPath("$.me.score").value(0))
                .andDo(document("rankings-member-without-score"));
    }

    @Test
    @DisplayName("랭킹이 비어 있으면 entries는 빈 배열이다")
    void getEmptyRanking() throws Exception {
        given(rankingService.getRanking(null)).willReturn(new RankingResponse(List.of(), null));

        mvc.perform(get(RANKINGS))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"entries\": [], \"me\": null}", JsonCompareMode.STRICT));
    }

    // 카테고리 랭킹

    @Test
    @DisplayName("카테고리 랭킹은 전체 랭킹과 같은 모양이다 (R5)")
    void getCategoryRankingOfMember() throws Exception {
        given(rankingService.getCategoryRanking(SLUG, USER_ID))
                .willReturn(new RankingResponse(ENTRIES, new MyRankingResponse(3L, 9L)));

        mvc.perform(get(CATEGORY_RANKINGS, SLUG).cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("entries", "me")))
                .andExpect(jsonPath("$.entries[0].keys()", containsInAnyOrder("rank", "nickname", "tag", "score")))
                .andExpect(jsonPath("$.entries[0].nickname").value("철수"))
                .andExpect(jsonPath("$.me.rank").value(3))
                .andExpect(jsonPath("$.me.score").value(9))
                .andExpect(noPersonalIdentifiers())
                .andDo(document(
                        "categories-rankings",
                        pathParameters(parameterWithName("slug").description("카테고리 식별자")),
                        requestCookies(cookieWithName(COOKIE_NAME)
                                .optional()
                                .description("로그인할 때 받은 인증 쿠키 (없으면 me 없이 목록만 받는다)")),
                        responseFields(rankingFields())));

        then(rankingService).should().getCategoryRanking(SLUG, USER_ID);
    }

    @Test
    @DisplayName("비회원도 카테고리 랭킹을 볼 수 있고 me는 null이다 (R8)")
    void getCategoryRankingOfGuest() throws Exception {
        given(rankingService.getCategoryRanking(SLUG, null)).willReturn(new RankingResponse(ENTRIES, null));

        mvc.perform(get(CATEGORY_RANKINGS, SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(nullValue()));

        then(rankingService).should().getCategoryRanking(SLUG, null);
    }

    @Test
    @DisplayName("카테고리 랭킹도 토큰이 만료되면 비회원으로 조회한다 (R8)")
    void getCategoryRankingWithExpiredToken() throws Exception {
        given(rankingService.getCategoryRanking(SLUG, null)).willReturn(new RankingResponse(ENTRIES, null));

        mvc.perform(get(CATEGORY_RANKINGS, SLUG).cookie(new Cookie(COOKIE_NAME, TestJwts.expired(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").value(nullValue()));

        then(rankingService).should().getCategoryRanking(SLUG, null);
    }

    @Test
    @DisplayName("없거나 삭제된 카테고리의 랭킹은 404 ProblemDetail이다 (R9)")
    void getCategoryRankingOfUnknownCategory() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("nope");
        given(rankingService.getCategoryRanking("nope", null)).willThrow(exception);

        mvc.perform(get(CATEGORY_RANKINGS, "nope"))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("categories-rankings-not-found"));
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("만료", TestJwts.expired(USER_ID)),
                Arguments.of("다른 키로 서명", TestJwts.signedWithOtherKey(USER_ID)),
                Arguments.of("본문 위조", TestJwts.tampered(USER_ID, 1L)),
                Arguments.of("서명 없음", TestJwts.unsigned(USER_ID)),
                Arguments.of("형식 아님", "not-a-jwt"));
    }

    private static FieldDescriptor[] rankingFields() {
        return new FieldDescriptor[] {
            fieldWithPath("entries").description("상위 50명 (등수 순). 랭킹이 비어 있으면 빈 배열"),
            fieldWithPath("entries[].rank").description("등수. 같은 등수는 없다"),
            fieldWithPath("entries[].nickname").description("닉네임"),
            fieldWithPath("entries[].tag").description("번호. 화면에는 `닉네임#번호`로 보인다"),
            fieldWithPath("entries[].score").description("점수 (맞힌 문제 수)"),
            fieldWithPath("me").type(JsonFieldType.OBJECT).description("내 순위. 비회원·관리자면 null"),
            fieldWithPath("me.rank").type(JsonFieldType.NUMBER).description("내 등수. 점수가 0이면 null (순위 없음)"),
            fieldWithPath("me.score").description("내 점수")
        };
    }

    private static ResultMatcher noPersonalIdentifiers() {
        return result -> {
            jsonPath("$..email").isEmpty().match(result);
            jsonPath("$..id").isEmpty().match(result);
            jsonPath("$..userId").isEmpty().match(result);
        };
    }

    private static Cookie validCookie() {
        return new Cookie(COOKIE_NAME, TestJwts.valid(USER_ID));
    }

    private static ResultMatcher problemDetail(HttpStatus expected) {
        return result -> {
            status().is(expected.value()).match(result);
            content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(expected.value()).match(result);
            jsonPath("$.title").exists().match(result);
        };
    }
}
