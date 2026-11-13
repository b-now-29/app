package com.b.now._29.app.quiz.wronganswer;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.QuestionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WrongAnswerService {

    private final WrongAnswerRepository wrongAnswerRepository;
    private final CategoryRepository categoryRepository;

    public WrongAnswerService(WrongAnswerRepository wrongAnswerRepository, CategoryRepository categoryRepository) {
        this.wrongAnswerRepository = wrongAnswerRepository;
        this.categoryRepository = categoryRepository;
    }

    /** 행이 온 순서(카테고리 순, 문제 순)를 유지해 카테고리별로 묶는다. */
    public List<WrongAnswerCategoryResponse> getWrongAnswers(long userId) {
        Map<String, List<WrongAnswerRow>> bySlug = new LinkedHashMap<>();
        for (WrongAnswerRow row : wrongAnswerRepository.findWrongAnswers(userId)) {
            bySlug.computeIfAbsent(row.slug(), slug -> new ArrayList<>()).add(row);
        }
        return bySlug.values().stream()
                .map(rows -> new WrongAnswerCategoryResponse(
                        rows.getFirst().slug(),
                        rows.getFirst().name(),
                        rows.stream().map(WrongAnswerQuestionResponse::from).toList()))
                .toList();
    }

    public List<QuestionResponse> getWrongQuestions(long userId, String slug) {
        if (!categoryRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new CategoryNotFoundException(slug);
        }
        return wrongAnswerRepository.findWrongQuestions(userId, slug).stream()
                .map(QuestionResponse::from)
                .toList();
    }
}
