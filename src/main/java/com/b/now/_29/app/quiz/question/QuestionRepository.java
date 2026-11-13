package com.b.now._29.app.quiz.question;

import com.b.now._29.app.quiz.category.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 삭제한 문제도 {@code findById}와 풀이 기록으로는 보여야 하므로 삭제 제외 조건은 아래 조회 메서드에만 붙인다. */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** 삭제하지 않은 문제만 표시 순서로 조회한다. */
    @EntityGraph(attributePaths = "choices")
    @Query("""
            select q from Question q
            where q.category.slug = :slug and q.type in :types and q.deletedAt is null
            order by q.sortOrder asc, q.id asc
            """)
    List<Question> findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(
            @Param("slug") String slug, @Param("types") Collection<QuestionType> types);

    /** 삭제하지 않은 문제를 보기와 함께 조회한다. */
    @EntityGraph(attributePaths = "choices")
    @Query("select q from Question q where q.id = :id and q.deletedAt is null")
    Optional<Question> findWithChoicesById(@Param("id") Long id);

    Optional<Question> findByIdAndDeletedAtIsNull(Long id);

    long countByCategoryAndDeletedAtIsNull(Category category);
}
