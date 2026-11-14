package com.b.now._29.app.quiz.ranking;

import com.b.now._29.app.quiz.question.Question;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 랭킹 집계 (D3). 요청마다 계산한다. 쿼리 조각은 {@link RankingSql}에 있다. */
public interface RankingRepository extends Repository<Question, Long> {

    /** 상위 limit명과 userId의 줄 (상위에 있으면 한 줄). userId가 null이면 상위 목록만 오고 mine은 모두 false다. */
    @Query(value = RankingSql.CHANGES + RankingSql.RANKED + RankingSql.RANKINGS, nativeQuery = true)
    List<RankingRow> findRankings(@Param("limit") int limit, @Param("userId") Long userId);

    @Query(value = RankingSql.CATEGORY_CHANGES + RankingSql.RANKED + RankingSql.RANKINGS, nativeQuery = true)
    List<RankingRow> findRankingsInCategory(
            @Param("slug") String slug, @Param("limit") int limit, @Param("userId") Long userId);
}
