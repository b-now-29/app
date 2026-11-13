package com.b.now._29.app.quiz.category;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
