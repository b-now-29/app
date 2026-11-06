package com.b.now._29.app.quiz.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 공개 카테고리 목록. 삭제한 카테고리는 뺀다. */
    List<Category> findAllByDeletedAtIsNullOrderBySortOrderAsc();

    /** 삭제한 카테고리도 센다. slug는 삭제 뒤에도 다시 쓰지 않는다 (D7). */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);
}
