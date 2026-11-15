package com.b.now._29.app.quiz.admin;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryAdminRepository extends JpaRepository<CategoryAdmin, Long> {

    List<CategoryAdmin> findAllByUserId(long userId);

    /** 회원 목록의 담당 카테고리용. 카테고리를 함께 읽어 N+1을 막는다. */
    @Query("select ca from CategoryAdmin ca join fetch ca.category where ca.user.id in :userIds")
    List<CategoryAdmin> findAllWithCategoryByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /** 이 카테고리를 담당하는 회원 id. 삭제 전에 users 행을 잠글 대상이다. */
    @Query("select ca.user.id from CategoryAdmin ca where ca.category.id = :categoryId")
    List<Long> findUserIdsByCategoryId(@Param("categoryId") long categoryId);

    void deleteAllByUserId(long userId);

    /** 삭제되지 않은 담당이 이 카테고리 하나뿐인 관리자 수. 이 카테고리를 지우면 담당이 0개가 되는 관리자다. */
    @Query("""
            select count(ca)
            from CategoryAdmin ca
            where ca.category.id = :categoryId
              and ca.category.deletedAt is null
              and (select count(other)
                   from CategoryAdmin other
                   where other.user.id = ca.user.id and other.category.deletedAt is null) = 1
            """)
    long countAdminsWithOnlyCategory(@Param("categoryId") long categoryId);
}
