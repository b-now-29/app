package com.b.now._29.app.quiz.ranking;

/**
 * 랭킹 집계 SQL 조각 (D3). 기록마다 같은 (회원, 문제)의 직전 기록과 비교해 점수 변화 (+1, 0, -1)를 구하고, 회원별로 변화의 합을 점수, 변화가 0이 아닌 마지막 시각을 도달 시각으로
 * 한다. 삭제된 문제·카테고리는 이력 계산 전에 뺀다 (R2).
 */
final class RankingSql {

    private RankingSql() {}

    /** 점수 변화 계산. 점수 규칙은 여기 한 곳에만 둔다. 카테고리 조인 조건 앞까지다. */
    private static final String CHANGES_HEAD = """
            WITH changes AS (
                SELECT a.user_id,
                       a.attempted_at,
                       (CASE WHEN a.correct THEN 1 ELSE 0 END)
                           - (CASE WHEN COALESCE(LAG(a.correct) OVER w, FALSE) THEN 1 ELSE 0 END) AS delta
                FROM question_attempt a
                    JOIN question q ON q.id = a.question_id AND q.deleted_at IS NULL
                    JOIN category c ON c.id = q.category_id AND c.deleted_at IS NULL""";

    private static final String CHANGES_TAIL = """

                WINDOW w AS (PARTITION BY a.user_id, a.question_id ORDER BY a.attempted_at, a.id)
            ),
            """;

    /** 전체 랭킹: 카테고리 조건 없이 모든 카테고리의 기록을 쓴다. */
    static final String CHANGES = CHANGES_HEAD + CHANGES_TAIL;

    /** 카테고리 랭킹: 그 카테고리 문제의 기록만 이력에 넣는다 (R5). */
    static final String CATEGORY_CHANGES = CHANGES_HEAD + " AND c.slug = :slug" + CHANGES_TAIL;

    /** 점수 0 (R3)과 관리자 (R3)를 빼고 등수를 매긴다. 점수 desc, 도달 시각 asc, 회원 id asc (R4). */
    static final String RANKED = """
            scores AS (
                SELECT user_id,
                       SUM(delta) AS score,
                       MAX(attempted_at) FILTER (WHERE delta <> 0) AS reached_at
                FROM changes
                GROUP BY user_id
                HAVING SUM(delta) > 0
            ),
            ranked AS (
                SELECT ROW_NUMBER() OVER (ORDER BY s.score DESC, s.reached_at ASC, s.user_id ASC) AS rank,
                       u.nickname AS nickname,
                       u.tag AS tag,
                       s.score AS score,
                       s.user_id AS user_id
                FROM scores s
                    JOIN users u ON u.id = s.user_id AND u.role <> 'ADMIN'
            )
            """;

    static final String TOP = "SELECT rank, nickname, tag, score FROM ranked ORDER BY rank LIMIT :limit";

    static final String USER = "SELECT rank, nickname, tag, score FROM ranked WHERE user_id = :userId";
}
