package com.b.now._29.app.quiz.category;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 공개 카테고리 목록. 삭제한 카테고리는 빼고 sortOrder가 같으면 id 순. 관리자 통계도 같은 순서를 쓴다. */
    List<Category> findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc();

    /** 삭제한 카테고리도 센다. slug는 삭제 뒤에도 다시 쓰지 않는다 (D7). */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);

    /**
     * 카테고리 삭제용. 행을 FOR UPDATE로 잠가 같은 카테고리에 문제를 추가·수정하는 트랜잭션과 직렬화한다. 문제 FK의 FOR KEY SHARE와
     * soft delete UPDATE(키 컬럼을 안 바꾸는 FOR NO KEY UPDATE)는 서로 막지 않아서, 잠그지 않으면 삭제 중에 문제가 붙을 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Category> findWithWriteLockBySlugAndDeletedAtIsNull(String slug);

    /** 문제 추가·수정용. 삭제 ({@link #findWithWriteLockBySlugAndDeletedAtIsNull})와 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Category> findWithShareLockBySlugAndDeletedAtIsNull(String slug);
}
