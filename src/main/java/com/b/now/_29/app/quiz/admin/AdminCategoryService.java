package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.category.DuplicateCategorySlugException;
import com.b.now._29.app.quiz.question.QuestionRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;
    private final AdminCategoryRepository adminCategoryRepository;

    public AdminCategoryService(
            CategoryRepository categoryRepository,
            QuestionRepository questionRepository,
            AdminCategoryRepository adminCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.questionRepository = questionRepository;
        this.adminCategoryRepository = adminCategoryRepository;
    }

    public List<AdminCategoryResponse> getCategories() {
        return adminCategoryRepository.findAdminCategories();
    }

    /** 삭제한 카테고리의 slug도 다시 쓸 수 없다 (D7). */
    @Transactional
    public AdminCategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new DuplicateCategorySlugException(request.slug());
        }
        try {
            Category saved = categoryRepository.saveAndFlush(
                    new Category(request.slug(), request.name(), request.description(), request.sortOrder()));
            return AdminCategoryResponse.from(saved, 0);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategorySlugException(request.slug(), e);
        }
    }

    @Transactional
    public AdminCategoryResponse update(String slug, CategoryUpdateRequest request) {
        Category category = findActive(slug);
        category.update(request.name(), request.description(), request.sortOrder());
        return AdminCategoryResponse.from(category, questionRepository.countByCategoryAndDeletedAtIsNull(category));
    }

    @Transactional
    public void delete(String slug) {
        Category category = findActive(slug);
        category.delete(questionRepository.countByCategoryAndDeletedAtIsNull(category));
    }

    private Category findActive(String slug) {
        return categoryRepository
                .findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
    }
}
