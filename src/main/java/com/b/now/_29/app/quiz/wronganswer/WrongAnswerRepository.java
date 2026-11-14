package com.b.now._29.app.quiz.wronganswer;

import com.b.now._29.app.quiz.attempt.LatestAttemptJpql;
import com.b.now._29.app.quiz.question.Question;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 오답 조회. 문제마다 가장 최근 기록(시각이 같으면 id가 큰 것)이 틀림인 문제만 돌려준다 (R1, D3). */
public interface WrongAnswerRepository extends Repository<Question, Long> {

    @Query("""
            select new com.b.now._29.app.quiz.wronganswer.WrongAnswerRow(
                c.slug, c.name, q.id, q.content, q.type, a.attemptedAt)
            from QuestionAttempt a""" + LatestAttemptJpql.JOIN_LATEST_PER_QUESTION + """
                join a.question q
                join q.category c
            where a.correct = false
              and q.deletedAt is null
              and c.deletedAt is null
            order by c.sortOrder asc, c.id asc, a.attemptedAt desc, a.id desc
            """)
    List<WrongAnswerRow> findWrongAnswers(@Param("userId") long userId);

    @EntityGraph(attributePaths = "choices")
    @Query("""
            select q from QuestionAttempt a""" + LatestAttemptJpql.JOIN_LATEST_PER_QUESTION + """
                join a.question q
                join q.category c
            where c.slug = :slug
              and c.deletedAt is null
              and q.deletedAt is null
              and a.correct = false
            order by q.sortOrder asc, q.id asc
            """)
    List<Question> findWrongQuestions(@Param("userId") long userId, @Param("slug") String slug);
}
