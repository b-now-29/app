package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 문제 수를 함께 세는 관리자 조회. category는 question을 참조하지 않으므로 admin에 둔다 (D15). */
public interface AdminCategoryRepository extends Repository<Category, Long> {

    @Query("""
            select new com.b.now._29.app.quiz.admin.AdminCategoryResponse(
                c.slug, c.name, c.description, c.sortOrder, count(q))
            from Category c
                left join Question q on q.category = c and q.deletedAt is null
            where c.deletedAt is null
            group by c.id, c.slug, c.name, c.description, c.sortOrder
            order by c.sortOrder asc, c.id asc
            """)
    List<AdminCategoryResponse> findAdminCategories();
}
