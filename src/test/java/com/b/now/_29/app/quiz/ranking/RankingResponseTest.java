package com.b.now._29.app.quiz.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구현할 API.
 *
 * <ul>
 *   <li>{@code record RankingViewer(Long memberId)}: package-private, {@code ANONYMOUS}, {@code boolean hasMe()}
 *   <li>{@code static RankingResponse RankingResponse.of(List<RankingRow> rows, RankingViewer viewer)}
 *   <li>{@code static Optional<RankingRow> RankingRow.mineIn(List<RankingRow> rows)}
 *   <li>{@code static MyRankingResponse MyRankingResponse.from(RankingRow)}, {@code unranked()}
 * </ul>
 */
class RankingResponseTest {

    private static final RankingViewer MEMBER = new RankingViewer(7L);

    private static final RankingRow FIRST = new RankingRow(1L, "철수", 3, 42L, true, false);
    private static final RankingRow SECOND = new RankingRow(2L, "영희", 1, 40L, true, false);

    private static final List<RankingEntryResponse> TOP_ENTRIES =
            List.of(new RankingEntryResponse(1L, "철수", 3, 42L), new RankingEntryResponse(2L, "영희", 1, 40L));

    @Test
    @DisplayName("inTop인 행만 entries에 들어간다 (R3, D9)")
    void entriesHoldOnlyInTopRows() {
        RankingRow outside = new RankingRow(51L, "밖", 2, 1L, false, false);

        RankingResponse response = RankingResponse.of(List.of(FIRST, SECOND, outside), MEMBER);

        assertThat(response.entries()).isEqualTo(TOP_ENTRIES);
    }

    @Test
    @DisplayName("50위 밖 내 줄은 me에만 들어간다 (R3)")
    void mineOutsideTopIsOnlyInMe() {
        RankingRow mine = new RankingRow(120L, "나", 2, 5L, false, true);

        RankingResponse response = RankingResponse.of(List.of(FIRST, SECOND, mine), MEMBER);

        assertThat(response).isEqualTo(new RankingResponse(TOP_ENTRIES, new MyRankingResponse(120L, 5L)));
    }

    @Test
    @DisplayName("50위 안 내 줄은 entries와 me 모두에 들어간다 (R3)")
    void mineInsideTopIsInEntriesAndMe() {
        RankingRow mine = new RankingRow(2L, "영희", 1, 40L, true, true);

        RankingResponse response = RankingResponse.of(List.of(FIRST, mine), MEMBER);

        assertThat(response).isEqualTo(new RankingResponse(TOP_ENTRIES, new MyRankingResponse(2L, 40L)));
    }

    @Test
    @DisplayName("비회원 (ANONYMOUS)이면 me가 null이다 (R8, D8)")
    void anonymousHasNoMe() {
        RankingResponse response = RankingResponse.of(List.of(FIRST, SECOND), RankingViewer.ANONYMOUS);

        assertThat(response).isEqualTo(new RankingResponse(TOP_ENTRIES, null));
    }

    @Test
    @DisplayName("회원인데 mine 행이 없으면 me는 rank null, score 0이다 (R7, D6)")
    void memberWithoutMineRowIsUnranked() {
        RankingResponse response = RankingResponse.of(List.of(FIRST, SECOND), MEMBER);

        assertThat(response.me()).isEqualTo(new MyRankingResponse(null, 0L));
    }

    @Test
    @DisplayName("행이 하나도 없어도 회원은 unranked me, entries는 빈 목록이다")
    void emptyRowsForMember() {
        assertThat(RankingResponse.of(List.of(), MEMBER))
                .isEqualTo(new RankingResponse(List.of(), new MyRankingResponse(null, 0L)));
    }

    @Test
    @DisplayName("RankingViewer는 회원 id가 있을 때만 me를 가진다 (D8)")
    void viewerHasMe() {
        assertThat(MEMBER.hasMe()).isTrue();
        assertThat(RankingViewer.ANONYMOUS.hasMe()).isFalse();
        assertThat(RankingViewer.ANONYMOUS.memberId()).isNull();
    }

    @Test
    @DisplayName("mineIn은 mine인 첫 행을 돌려주고 없으면 empty다 (D10)")
    void mineInFindsMineRow() {
        RankingRow mine = new RankingRow(120L, "나", 2, 5L, false, true);

        assertThat(RankingRow.mineIn(List.of(FIRST, SECOND, mine))).contains(mine);
        assertThat(RankingRow.mineIn(List.of(FIRST, SECOND))).isEmpty();
        assertThat(RankingRow.mineIn(List.of())).isEmpty();
    }

    @Test
    @DisplayName("MyRankingResponse.from은 행의 등수와 점수를, unranked는 rank null, score 0을 담는다 (D6)")
    void myRankingResponseFactories() {
        assertThat(MyRankingResponse.from(new RankingRow(120L, "나", 2, 5L, false, true)))
                .isEqualTo(new MyRankingResponse(120L, 5L));
        assertThat(MyRankingResponse.unranked()).isEqualTo(new MyRankingResponse(null, 0L));
    }
}
