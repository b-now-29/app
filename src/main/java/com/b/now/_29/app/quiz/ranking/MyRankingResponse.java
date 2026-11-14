package com.b.now._29.app.quiz.ranking;

import java.util.Optional;

/** 내 순위. 점수가 0이라 랭킹에 없으면 rank는 null이다 (R7). */
public record MyRankingResponse(Long rank, long score) {

    static MyRankingResponse from(Optional<RankingRow> row) {
        return row.map(r -> new MyRankingResponse(r.rank(), r.score()))
                .orElseGet(() -> new MyRankingResponse(null, 0L));
    }
}
