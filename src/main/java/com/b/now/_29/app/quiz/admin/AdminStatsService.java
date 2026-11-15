package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    private final AdminStatsRepository adminStatsRepository;
    private final CategoryRepository categoryRepository;
    private final AdminScopeService adminScopeService;

    public AdminStatsService(
            AdminStatsRepository adminStatsRepository,
            CategoryRepository categoryRepository,
            AdminScopeService adminScopeService) {
        this.adminStatsRepository = adminStatsRepository;
        this.categoryRepository = categoryRepository;
        this.adminScopeService = adminScopeService;
    }

    /** OWNER는 전체, 카테고리 관리자는 담당 카테고리만. */
    public List<AdminCategoryStatResponse> getCategoryStats(long actorId) {
        AdminScope scope = adminScopeService.of(actorId);
        Map<String, List<AdminTypeStatResponse>> typesBySlug = adminStatsRepository.findCategoryTypeStats().stream()
                .sorted(Comparator.comparing(AdminCategoryTypeStat::type))
                .collect(Collectors.groupingBy(
                        AdminCategoryTypeStat::slug,
                        Collectors.mapping(
                                row -> new AdminTypeStatResponse(row.type(), row.solved(), row.correct()),
                                Collectors.toUnmodifiableList())));
        return categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .filter(scope::canManage)
                .map(category -> new AdminCategoryStatResponse(
                        category.getSlug(),
                        category.getName(),
                        typesBySlug.getOrDefault(category.getSlug(), List.of())))
                .toList();
    }

    public List<AdminQuestionStatResponse> getQuestionStats(long actorId, String categorySlug) {
        AdminScope scope = adminScopeService.of(actorId);
        Category category = categoryRepository
                .findBySlugAndDeletedAtIsNull(categorySlug)
                .orElseThrow(() -> new CategoryNotFoundException(categorySlug));
        scope.requireManage(category);
        return List.copyOf(adminStatsRepository.findQuestionStats(categorySlug));
    }
}
