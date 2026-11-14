package com.b.now._29.app.quiz.ranking;

import com.b.now._29.app.quiz.question.Question;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 랭킹 집계 (D3). 요청마다 계산한다. 쿼리 조각은 {@link RankingSql}에 있다. */
public interface RankingRepository extends Repository<Question, Long> {

    @Query(value = RankingSql.CHANGES + RankingSql.RANKED + RankingSql.TOP, nativeQuery = true)
    List<RankingRow> findTopRankings(@Param("limit") int limit);

    @Query(value = RankingSql.CHANGES + RankingSql.RANKED + RankingSql.USER, nativeQuery = true)
    Optional<RankingRow> findUserRanking(@Param("userId") long userId);

    @Query(value = RankingSql.CATEGORY_CHANGES + RankingSql.RANKED + RankingSql.TOP, nativeQuery = true)
    List<RankingRow> findTopRankingsInCategory(@Param("slug") String slug, @Param("limit") int limit);

    @Query(value = RankingSql.CATEGORY_CHANGES + RankingSql.RANKED + RankingSql.USER, nativeQuery = true)
    Optional<RankingRow> findUserRankingInCategory(@Param("slug") String slug, @Param("userId") long userId);
}
