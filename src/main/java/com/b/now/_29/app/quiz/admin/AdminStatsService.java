package com.b.now._29.app.quiz.admin;

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

    public AdminStatsService(AdminStatsRepository adminStatsRepository, CategoryRepository categoryRepository) {
        this.adminStatsRepository = adminStatsRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<AdminCategoryStatResponse> getCategoryStats() {
        Map<String, List<AdminTypeStatResponse>> typesBySlug = adminStatsRepository.findCategoryTypeStats().stream()
                .sorted(Comparator.comparing(AdminCategoryTypeStat::type))
                .collect(Collectors.groupingBy(
                        AdminCategoryTypeStat::slug,
                        Collectors.mapping(
                                row -> new AdminTypeStatResponse(row.type(), row.solved(), row.correct()),
                                Collectors.toUnmodifiableList())));
        return categoryRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .map(category -> new AdminCategoryStatResponse(
                        category.getSlug(),
                        category.getName(),
                        typesBySlug.getOrDefault(category.getSlug(), List.of())))
                .toList();
    }

    public List<AdminQuestionStatResponse> getQuestionStats(String categorySlug) {
        if (!categoryRepository.existsBySlugAndDeletedAtIsNull(categorySlug)) {
            throw new CategoryNotFoundException(categorySlug);
        }
        return List.copyOf(adminStatsRepository.findQuestionStats(categorySlug));
    }
}
