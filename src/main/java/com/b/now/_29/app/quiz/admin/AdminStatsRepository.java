package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.question.Question;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 관리자 통계 집계. 회원·문제마다 가장 최근 기록 1건(시각이 같으면 id가 큰 것)만 센다 (R1). */
public interface AdminStatsRepository extends Repository<Question, Long> {

    @Query("""
            select new com.b.now._29.app.quiz.admin.AdminCategoryTypeStat(
                c.slug, q.type, count(a), sum(case when a.correct then 1L else 0L end))
            from QuestionAttempt a
                join a.question q
                join q.category c
            where q.deletedAt is null
              and c.deletedAt is null
              and a.id = (
                select a2.id from QuestionAttempt a2
                where a2.user = a.user and a2.question = a.question
                order by a2.attemptedAt desc, a2.id desc
                limit 1)
            group by c.slug, q.type
            """)
    List<AdminCategoryTypeStat> findCategoryTypeStats();

    /** 문제 기준 left join이므로 최근 1건 조건은 ON 절에 둔다. WHERE에 두면 기록 0건 문제가 사라진다 (D10). */
    @Query("""
            select new com.b.now._29.app.quiz.admin.AdminQuestionStatResponse(
                q.id, q.type, q.content, count(a), coalesce(sum(case when a.correct then 1L else 0L end), 0L))
            from Question q
                join q.category c
                left join QuestionAttempt a on a.question = q
                    and a.id = (
                        select a2.id from QuestionAttempt a2
                        where a2.user = a.user and a2.question = q
                        order by a2.attemptedAt desc, a2.id desc
                        limit 1)
            where c.slug = :slug
              and c.deletedAt is null
              and q.deletedAt is null
            group by q.id, q.type, q.content, q.sortOrder
            order by q.sortOrder asc, q.id asc
            """)
    List<AdminQuestionStatResponse> findQuestionStats(@Param("slug") String categorySlug);
}
