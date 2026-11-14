package com.b.now._29.app.quiz.attempt;

/**
 * "문제별 최근 기록" JPQL 조각. 다른 기능 패키지의 Repository가 써도 되는 공용 조각이다.
 *
 * <p>{@code from QuestionAttempt a} 바로 뒤에 이어 붙이면 {@code a}가 회원의 기록 중 문제마다 가장 최근 1건
 * (attemptedAt desc, 같으면 id desc)으로 좁혀진다. 회원 범위도 조각이 정하므로 바깥 where에 회원 조건을 따로 두지 않는다.
 *
 * <p>조각이 요구하는 조건:
 *
 * <ul>
 *   <li>바깥 쿼리의 기록 별칭은 {@code QuestionAttempt a}여야 한다.
 *   <li>쿼리에 {@code :userId} 파라미터가 있어야 한다.
 *   <li>{@code from QuestionAttempt a} 바로 뒤에 붙인다.
 * </ul>
 */
public final class LatestAttemptJpql {

    public static final String JOIN_LATEST_PER_QUESTION = """

                join (select a2.id as id,
                             row_number() over (partition by a2.question.id
                                                order by a2.attemptedAt desc, a2.id desc) as rn
                      from QuestionAttempt a2
                      where a2.user.id = :userId) latest on latest.id = a.id and latest.rn = 1
            """;

    private LatestAttemptJpql() {}
}
