package com.b.now._29.app.quiz.ranking;

import java.util.List;

/** me는 비회원·관리자면 null이다 (R7, R8). */
public record RankingResponse(List<RankingEntryResponse> entries, MyRankingResponse me) {

    public RankingResponse {
        entries = List.copyOf(entries);
    }
}
