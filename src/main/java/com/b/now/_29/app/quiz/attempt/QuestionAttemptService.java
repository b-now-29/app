package com.b.now._29.app.quiz.attempt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QuestionAttemptService {

    private final QuestionAttemptRepository questionAttemptRepository;

    public QuestionAttemptService(QuestionAttemptRepository questionAttemptRepository) {
        this.questionAttemptRepository = questionAttemptRepository;
    }

    public List<CategoryStatResponse> getCategoryStats(long userId) {
        Map<String, List<CategoryTypeStat>> bySlug = new LinkedHashMap<>();
        for (CategoryTypeStat stat : questionAttemptRepository.findCategoryTypeStats(userId)) {
            bySlug.computeIfAbsent(stat.slug(), slug -> new ArrayList<>()).add(stat);
        }
        return bySlug.values().stream().map(QuestionAttemptService::toCategory).toList();
    }

    public List<AttemptResponse> getRecentAttempts(long userId) {
        return questionAttemptRepository.findTop20ByUserIdOrderByAttemptedAtDescIdDesc(userId).stream()
                .map(AttemptResponse::from)
                .toList();
    }

    private static CategoryStatResponse toCategory(List<CategoryTypeStat> stats) {
        CategoryTypeStat first = stats.getFirst();
        // QuestionType 선언 순서가 MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY다
        List<TypeStatResponse> types = stats.stream()
                .sorted(Comparator.comparing(CategoryTypeStat::type))
                .map(TypeStatResponse::from)
                .toList();
        return new CategoryStatResponse(first.slug(), first.name(), types);
    }
}
