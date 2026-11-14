package com.b.now._29.app.quiz.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.user.UserRoleService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 구현할 API.
 *
 * <ul>
 *   <li>{@code RankingService(RankingRepository, CategoryRepository, UserRoleService)}
 *   <li>{@code RankingResponse getRanking(Long userId)}: userId는 비회원이면 null
 *   <li>{@code RankingResponse getCategoryRanking(String slug, Long userId)}: 없는·삭제된 카테고리는
 *       {@code CategoryNotFoundException}
 *   <li>{@code record RankingResponse(List<RankingEntryResponse> entries, MyRankingResponse me)}
 *   <li>{@code record RankingEntryResponse(long rank, String nickname, int tag, long score)}
 *   <li>{@code record MyRankingResponse(Long rank, long score)}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final long USER_ID = 7L;
    private static final int TOP_LIMIT = 50;
    private static final String SLUG = "java";

    private static final List<RankingRow> ROWS =
            List.of(new RankingRow(1L, "철수", 3, 42L, true, false), new RankingRow(2L, "영희", 1, 40L, true, false));
    private static final List<RankingEntryResponse> ENTRIES =
            List.of(new RankingEntryResponse(1L, "철수", 3, 42L), new RankingEntryResponse(2L, "영희", 1, 40L));

    @Mock
    RankingRepository rankingRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    UserRoleService userRoleService;

    @InjectMocks
    RankingService rankingService;

    private static List<RankingRow> withRow(RankingRow extra) {
        List<RankingRow> rows = new ArrayList<>(ROWS);
        rows.add(extra);
        return rows;
    }

    // 전체 랭킹

    @Test
    @DisplayName("상위 50명을 조회해 줄마다 등수, 닉네임, 번호, 점수로 바꾼다 (R6, D5)")
    void mapsTopFiftyRows() {
        given(rankingRepository.findRankings(TOP_LIMIT, null)).willReturn(ROWS);

        assertThat(rankingService.getRanking(null).entries()).isEqualTo(ENTRIES);
    }

    @Test
    @DisplayName("비회원은 me가 null이고 null로 한 번만 조회하며 role을 조회하지 않는다 (R1, R8)")
    void guestHasNoMe() {
        given(rankingRepository.findRankings(TOP_LIMIT, null)).willReturn(ROWS);

        assertThat(rankingService.getRanking(null)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(times(1)).findRankings(TOP_LIMIT, null);
        then(rankingRepository).shouldHaveNoMoreInteractions();
        then(userRoleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("랭킹이 비어 있으면 entries는 빈 목록이다")
    void emptyRanking() {
        given(rankingRepository.findRankings(TOP_LIMIT, null)).willReturn(List.of());

        assertThat(rankingService.getRanking(null)).isEqualTo(new RankingResponse(List.of(), null));
    }

    @Test
    @DisplayName("50위 밖 회원은 행을 entries와 me로 나눈다. entries에는 50위 이내 행만 들어간다 (R3, R7, D4)")
    void splitsRowsIntoEntriesAndMe() {
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankings(TOP_LIMIT, USER_ID))
                .willReturn(withRow(new RankingRow(77L, "나", 2, 5L, false, true)));

        assertThat(rankingService.getRanking(USER_ID))
                .isEqualTo(new RankingResponse(ENTRIES, new MyRankingResponse(77L, 5L)));
    }

    @Test
    @DisplayName("50위 안 회원의 줄은 entries와 me 모두에 있다 (R3, D4)")
    void memberInsideTopIsInEntriesAndMe() {
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankings(TOP_LIMIT, USER_ID))
                .willReturn(List.of(
                        new RankingRow(1L, "철수", 3, 42L, true, false), new RankingRow(2L, "영희", 1, 40L, true, true)));

        assertThat(rankingService.getRanking(USER_ID))
                .isEqualTo(new RankingResponse(ENTRIES, new MyRankingResponse(2L, 40L)));
    }

    @Test
    @DisplayName("점수가 0이라 랭킹에 없는 회원은 me가 rank null, score 0이다 (R7)")
    void memberWithoutScoreHasNoRank() {
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankings(TOP_LIMIT, USER_ID)).willReturn(ROWS);

        assertThat(rankingService.getRanking(USER_ID).me()).isEqualTo(new MyRankingResponse(null, 0L));
    }

    @Test
    @DisplayName("회원 랭킹 조회도 요청마다 한 번이다 (R1)")
    void memberQueriesOnce() {
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankings(TOP_LIMIT, USER_ID)).willReturn(ROWS);

        rankingService.getRanking(USER_ID);

        then(rankingRepository).should(times(1)).findRankings(TOP_LIMIT, USER_ID);
        then(rankingRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("관리자는 me가 null이고 userId 대신 null로 한 번만 조회한다 (R1, R3, R7, D4)")
    void adminHasNoMe() {
        given(userRoleService.isAdmin(USER_ID)).willReturn(true);
        given(rankingRepository.findRankings(TOP_LIMIT, null)).willReturn(ROWS);

        assertThat(rankingService.getRanking(USER_ID)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(times(1)).findRankings(TOP_LIMIT, null);
        then(rankingRepository).shouldHaveNoMoreInteractions();
    }

    // 카테고리 랭킹

    @Test
    @DisplayName("없거나 삭제된 카테고리는 CategoryNotFoundException이고 랭킹을 조회하지 않는다 (R9)")
    void unknownCategory() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull("nope")).willReturn(false);

        assertThatThrownBy(() -> rankingService.getCategoryRanking("nope", USER_ID))
                .isInstanceOf(CategoryNotFoundException.class);
        then(rankingRepository).should(never()).findRankingsInCategory(anyString(), anyInt(), any());
        then(rankingRepository).should(never()).findRankings(anyInt(), any());
    }

    @Test
    @DisplayName("카테고리 랭킹은 그 카테고리의 상위 50명을 null로 한 번 조회하고, 비회원은 me가 null이다 (R1, R5, R8)")
    void categoryRankingForGuest() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(rankingRepository.findRankingsInCategory(SLUG, TOP_LIMIT, null)).willReturn(ROWS);

        assertThat(rankingService.getCategoryRanking(SLUG, null)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(times(1)).findRankingsInCategory(SLUG, TOP_LIMIT, null);
        then(rankingRepository).shouldHaveNoMoreInteractions();
        then(userRoleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("카테고리 랭킹의 me는 그 카테고리 안의 자기 등수와 점수이고 조회는 한 번이다 (R1, R5, R7)")
    void categoryRankingForMember() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankingsInCategory(SLUG, TOP_LIMIT, USER_ID))
                .willReturn(withRow(new RankingRow(60L, "나", 2, 9L, false, true)));

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID))
                .isEqualTo(new RankingResponse(ENTRIES, new MyRankingResponse(60L, 9L)));
        then(rankingRepository).should(times(1)).findRankingsInCategory(SLUG, TOP_LIMIT, USER_ID);
        then(rankingRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("카테고리 상위 50명 안 회원의 줄은 entries와 me 모두에 있다 (R3, D4)")
    void categoryMemberInsideTop() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankingsInCategory(SLUG, TOP_LIMIT, USER_ID))
                .willReturn(List.of(
                        new RankingRow(1L, "철수", 3, 42L, true, true), new RankingRow(2L, "영희", 1, 40L, true, false)));

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID))
                .isEqualTo(new RankingResponse(ENTRIES, new MyRankingResponse(1L, 42L)));
    }

    @Test
    @DisplayName("카테고리에서 점수가 0인 회원은 me가 rank null, score 0이다 (R7)")
    void categoryRankingForMemberWithoutScore() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findRankingsInCategory(SLUG, TOP_LIMIT, USER_ID))
                .willReturn(List.of());

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID))
                .isEqualTo(new RankingResponse(List.of(), new MyRankingResponse(null, 0L)));
    }

    @Test
    @DisplayName("카테고리 랭킹에서도 관리자는 me가 null이고 null로 한 번만 조회한다 (R1, R3, D4)")
    void categoryRankingForAdmin() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(userRoleService.isAdmin(USER_ID)).willReturn(true);
        given(rankingRepository.findRankingsInCategory(SLUG, TOP_LIMIT, null)).willReturn(ROWS);

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(times(1)).findRankingsInCategory(SLUG, TOP_LIMIT, null);
        then(rankingRepository).shouldHaveNoMoreInteractions();
    }
}
