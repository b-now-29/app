package com.b.now._29.app.quiz.ranking;

import java.util.List;

/** me는 비회원·관리자면 null이다 (R7, R8). */
public record RankingResponse(List<RankingEntryResponse> entries, MyRankingResponse me) {

    public RankingResponse {
        entries = List.copyOf(entries);
    }

    /** inTop 행은 entries, 회원이면 mine 행이 me다. 랭킹에 없는 회원은 rank null, score 0 (R7, D9). */
    static RankingResponse of(List<RankingRow> rows, RankingViewer viewer) {
        List<RankingEntryResponse> entries = rows.stream()
                .filter(RankingRow::inTop)
                .map(RankingEntryResponse::from)
                .toList();
        MyRankingResponse me = viewer.hasMe()
                ? RankingRow.mineIn(rows).map(MyRankingResponse::from).orElseGet(MyRankingResponse::unranked)
                : null;
        return new RankingResponse(entries, me);
    }
}
