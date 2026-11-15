package com.b.now._29.app.support;

import com.b.now._29.app.quiz.category.Category;
import org.springframework.test.util.ReflectionTestUtils;

/** DB 없이 도는 테스트에서 id가 있는 카테고리를 만든다. 담당 범위 ({@code AdminScope})가 카테고리 id로 판단하므로 id가 필요하다. */
public final class TestCategories {

    private TestCategories() {}

    public static Category withId(long id, String slug) {
        Category category = new Category(slug, slug, 0);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
