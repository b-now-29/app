package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.category.DuplicateCategorySlugException;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.user.UserRepository;
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
    private final AdminScopeService adminScopeService;
    private final CategoryAdminRepository categoryAdminRepository;
    private final UserRepository userRepository;

    public AdminCategoryService(
            CategoryRepository categoryRepository,
            QuestionRepository questionRepository,
            AdminCategoryRepository adminCategoryRepository,
            AdminScopeService adminScopeService,
            CategoryAdminRepository categoryAdminRepository,
            UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.questionRepository = questionRepository;
        this.adminCategoryRepository = adminCategoryRepository;
        this.adminScopeService = adminScopeService;
        this.categoryAdminRepository = categoryAdminRepository;
        this.userRepository = userRepository;
    }

    /** OWNER는 전체, 카테고리 관리자는 담당 카테고리만. */
    public List<AdminCategoryResponse> getCategories(long actorId) {
        AdminScope scope = adminScopeService.of(actorId);
        if (scope.isOwner()) {
            return adminCategoryRepository.findAdminCategories();
        }
        if (scope.categoryIds().isEmpty()) {
            return List.of();
        }
        return adminCategoryRepository.findAdminCategoriesByIdIn(scope.categoryIds());
    }

    /** 삭제한 카테고리의 slug도 다시 쓸 수 없다 (D7). 경로 인가가 OWNER만 통과시킨다. */
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
    public AdminCategoryResponse update(long actorId, String slug, CategoryUpdateRequest request) {
        AdminScope scope = adminScopeService.of(actorId);
        Category category = findActive(slug);
        scope.requireManage(category);
        category.update(request.name(), request.description(), request.sortOrder());
        return AdminCategoryResponse.from(category, questionRepository.countByCategoryAndDeletedAtIsNull(category));
    }

    /** 경로 인가가 OWNER만 통과시킨다. */
    @Transactional
    public void delete(String slug) {
        Category category = findActiveForUpdate(slug);
        // 담당 관리자의 역할·담당 변경과 순서를 맞추려고 users 행을 id 순으로 잠근 뒤 센다 (D13)
        userRepository.findWithWriteLockByIdInOrderByIdAsc(
                categoryAdminRepository.findUserIdsByCategoryId(category.getId()));
        category.delete(
                questionRepository.countByCategoryAndDeletedAtIsNull(category),
                categoryAdminRepository.countAdminsWithOnlyCategory(category.getId()));
    }

    /** 문제 수를 세기 전에 행을 잠근다. */
    private Category findActiveForUpdate(String slug) {
        return categoryRepository
                .findWithWriteLockBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
    }

    private Category findActive(String slug) {
        return categoryRepository
                .findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
    }
}
