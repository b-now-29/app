package com.b.now._29.app.quiz.question;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = "choices")
    List<Question> findAllByCategorySlugAndTypeOrderBySortOrderAscIdAsc(String slug, QuestionType type);

    @EntityGraph(attributePaths = "choices")
    Optional<Question> findWithChoicesById(Long id);
}
