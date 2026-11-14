package com.b.now._29.app.quiz.ranking;

import java.util.List;
import java.util.Optional;

/**
 * 랭킹 한 줄. 이메일과 회원 id는 담지 않는다 (R6, D7). inTop은 상위 한도 안이면 true, mine은 조회한 회원 본인의 줄이면 true다. 둘 다 SQL이 판정한다.
 */
public record RankingRow(long rank, String nickname, int tag, long score, boolean inTop, boolean mine) {

    /** 조회한 회원 본인의 줄 (D10). 없으면 empty. */
    static Optional<RankingRow> mineIn(List<RankingRow> rows) {
        return rows.stream().filter(RankingRow::mine).findFirst();
    }
}
