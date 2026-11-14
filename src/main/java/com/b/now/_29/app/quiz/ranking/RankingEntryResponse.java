package com.b.now._29.app.quiz.ranking;

public record RankingEntryResponse(long rank, String nickname, int tag, long score) {

    static RankingEntryResponse from(RankingRow row) {
        return new RankingEntryResponse(row.rank(), row.nickname(), row.tag(), row.score());
    }
}
