package com.b.now._29.app.quiz.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.user.UserRoleService;
import java.util.List;
import java.util.Optional;
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
            List.of(new RankingRow(1L, "철수", 3, 42L), new RankingRow(2L, "영희", 1, 40L));
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

    // 전체 랭킹

    @Test
    @DisplayName("상위 50명을 조회해 줄마다 등수, 닉네임, 번호, 점수로 바꾼다 (R6, D5)")
    void mapsTopFiftyRows() {
        given(rankingRepository.findTopRankings(TOP_LIMIT)).willReturn(ROWS);

        assertThat(rankingService.getRanking(null).entries()).isEqualTo(ENTRIES);
    }

    @Test
    @DisplayName("비회원은 me가 null이고 회원 순위와 role을 조회하지 않는다 (R8)")
    void guestHasNoMe() {
        given(rankingRepository.findTopRankings(TOP_LIMIT)).willReturn(ROWS);

        assertThat(rankingService.getRanking(null)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(never()).findUserRanking(anyLong());
        then(userRoleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("랭킹이 비어 있으면 entries는 빈 목록이다")
    void emptyRanking() {
        given(rankingRepository.findTopRankings(TOP_LIMIT)).willReturn(List.of());

        assertThat(rankingService.getRanking(null)).isEqualTo(new RankingResponse(List.of(), null));
    }

    @Test
    @DisplayName("랭킹에 든 회원은 50위 밖이어도 me에 자기 등수와 점수를 받는다 (R7)")
    void memberGetsOwnRank() {
        given(rankingRepository.findTopRankings(TOP_LIMIT)).willReturn(ROWS);
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findUserRanking(USER_ID)).willReturn(Optional.of(new RankingRow(77L, "나", 2, 5L)));

        assertThat(rankingService.getRanking(USER_ID).me()).isEqualTo(new MyRankingResponse(77L, 5L));
    }

    @Test
    @DisplayName("점수가 0이라 랭킹에 없는 회원은 me가 rank null, score 0이다 (R7)")
    void memberWithoutScoreHasNoRank() {
        given(rankingRepository.findTopRankings(TOP_LIMIT)).willReturn(ROWS);
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findUserRanking(USER_ID)).willReturn(Optional.empty());

        assertThat(rankingService.getRanking(USER_ID).me()).isEqualTo(new MyRankingResponse(null, 0L));
    }

    @Test
    @DisplayName("관리자는 me가 null이고 자기 순위를 조회하지 않는다 (R3, R7, D4)")
    void adminHasNoMe() {
        given(rankingRepository.findTopRankings(TOP_LIMIT)).willReturn(ROWS);
        given(userRoleService.isAdmin(USER_ID)).willReturn(true);

        assertThat(rankingService.getRanking(USER_ID)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(never()).findUserRanking(anyLong());
    }

    // 카테고리 랭킹

    @Test
    @DisplayName("없거나 삭제된 카테고리는 CategoryNotFoundException이고 랭킹을 조회하지 않는다 (R9)")
    void unknownCategory() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull("nope")).willReturn(false);

        assertThatThrownBy(() -> rankingService.getCategoryRanking("nope", USER_ID))
                .isInstanceOf(CategoryNotFoundException.class);
        then(rankingRepository).should(never()).findTopRankingsInCategory(anyString(), anyInt());
        then(rankingRepository).should(never()).findUserRankingInCategory(anyString(), anyLong());
    }

    @Test
    @DisplayName("카테고리 랭킹은 그 카테고리의 상위 50명을 조회하고, 비회원은 me가 null이다 (R5, R8)")
    void categoryRankingForGuest() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(rankingRepository.findTopRankingsInCategory(SLUG, TOP_LIMIT)).willReturn(ROWS);

        assertThat(rankingService.getCategoryRanking(SLUG, null)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(never()).findTopRankings(anyInt());
        then(userRoleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("카테고리 랭킹의 me는 그 카테고리 안의 자기 등수와 점수다 (R5, R7)")
    void categoryRankingForMember() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(rankingRepository.findTopRankingsInCategory(SLUG, TOP_LIMIT)).willReturn(ROWS);
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findUserRankingInCategory(SLUG, USER_ID))
                .willReturn(Optional.of(new RankingRow(3L, "나", 2, 9L)));

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID))
                .isEqualTo(new RankingResponse(ENTRIES, new MyRankingResponse(3L, 9L)));
        then(rankingRepository).should(never()).findUserRanking(anyLong());
    }

    @Test
    @DisplayName("카테고리에서 점수가 0인 회원은 me가 rank null, score 0이다 (R7)")
    void categoryRankingForMemberWithoutScore() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(rankingRepository.findTopRankingsInCategory(SLUG, TOP_LIMIT)).willReturn(List.of());
        given(userRoleService.isAdmin(USER_ID)).willReturn(false);
        given(rankingRepository.findUserRankingInCategory(SLUG, USER_ID)).willReturn(Optional.empty());

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID))
                .isEqualTo(new RankingResponse(List.of(), new MyRankingResponse(null, 0L)));
    }

    @Test
    @DisplayName("카테고리 랭킹에서도 관리자는 me가 null이다 (R3, D4)")
    void categoryRankingForAdmin() {
        given(categoryRepository.existsBySlugAndDeletedAtIsNull(SLUG)).willReturn(true);
        given(rankingRepository.findTopRankingsInCategory(SLUG, TOP_LIMIT)).willReturn(ROWS);
        given(userRoleService.isAdmin(USER_ID)).willReturn(true);

        assertThat(rankingService.getCategoryRanking(SLUG, USER_ID)).isEqualTo(new RankingResponse(ENTRIES, null));
        then(rankingRepository).should(never()).findUserRankingInCategory(anyString(), anyLong());
    }
}
