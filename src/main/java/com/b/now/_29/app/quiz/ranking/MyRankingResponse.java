package com.b.now._29.app.quiz.ranking;

/** 내 순위. 점수가 0이라 랭킹에 없으면 rank는 null이다 (R7). */
public record MyRankingResponse(Long rank, long score) {

    static MyRankingResponse from(RankingRow row) {
        return new MyRankingResponse(row.rank(), row.score());
    }

    static MyRankingResponse unranked() {
        return new MyRankingResponse(null, 0L);
    }
}
