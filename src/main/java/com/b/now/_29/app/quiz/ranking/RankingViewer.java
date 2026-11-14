package com.b.now._29.app.quiz.ranking;

/** 랭킹을 보는 사람. 회원이 아니거나 관리자면 {@link #ANONYMOUS}다 (R3, R8). */
record RankingViewer(Long memberId) {

    static final RankingViewer ANONYMOUS = new RankingViewer(null);

    /** me를 채울 회원인지. */
    boolean hasMe() {
        return memberId != null;
    }
}
