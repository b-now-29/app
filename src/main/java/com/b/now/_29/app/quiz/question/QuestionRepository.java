package com.b.now._29.app.quiz.question;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = "choices")
    List<Question> findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(String slug, Collection<QuestionType> types);

    @EntityGraph(attributePaths = "choices")
    Optional<Question> findWithChoicesById(Long id);
}
