package com.b.now._29.app.quiz.attempt;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {

    /** 문제마다 가장 최근 기록(시각이 같으면 id가 큰 것)만 세어 카테고리·유형별로 집계한다. */
    @Query("""
            select new com.b.now._29.app.quiz.attempt.CategoryTypeStat(
                c.slug, c.name, c.sortOrder, q.type, count(a), sum(case when a.correct then 1L else 0L end))
            from QuestionAttempt a
                join a.question q
                join q.category c
            where a.user.id = :userId
              and a.id = (
                select a2.id from QuestionAttempt a2
                where a2.user.id = :userId and a2.question = a.question
                order by a2.attemptedAt desc, a2.id desc
                limit 1)
            group by c.slug, c.name, c.sortOrder, q.type
            order by c.sortOrder, c.slug
            """)
    List<CategoryTypeStat> findCategoryTypeStats(@Param("userId") long userId);

    @EntityGraph(attributePaths = {"question", "question.category"})
    List<QuestionAttempt> findTop20ByUserIdOrderByAttemptedAtDescIdDesc(Long userId);
}
