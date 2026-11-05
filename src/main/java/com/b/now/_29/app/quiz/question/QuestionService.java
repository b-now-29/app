package com.b.now._29.app.quiz.question;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QuestionService {

    private static final List<QuestionType> SOLVABLE_TYPES =
            List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY);

    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;

    public QuestionService(QuestionRepository questionRepository, CategoryRepository categoryRepository) {
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<QuestionResponse> getQuestions(String slug) {
        if (!categoryRepository.existsBySlug(slug)) {
            throw new CategoryNotFoundException(slug);
        }
        return questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(slug, SOLVABLE_TYPES).stream()
                .map(QuestionResponse::from)
                .toList();
    }

    public AnswerResponse submitAnswer(long questionId, long choiceId) {
        Question question = questionRepository
                .findWithChoicesById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        return AnswerResponse.from(question.grade(choiceId));
    }

    public TextAnswerResponse submitTextAnswer(long questionId) {
        Question question =
                questionRepository.findById(questionId).orElseThrow(() -> new QuestionNotFoundException(questionId));
        return TextAnswerResponse.from(question.revealModelAnswer());
    }
}
