package com.b.now._29.app.quiz.question;

import com.b.now._29.app.quiz.attempt.QuestionAttempt;
import com.b.now._29.app.quiz.attempt.QuestionAttemptRepository;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.user.UserRepository;
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

    private final QuestionAttemptRepository questionAttemptRepository;
    private final UserRepository userRepository;

    public QuestionService(
            QuestionRepository questionRepository,
            CategoryRepository categoryRepository,
            QuestionAttemptRepository questionAttemptRepository,
            UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.questionAttemptRepository = questionAttemptRepository;
        this.userRepository = userRepository;
    }

    public List<QuestionResponse> getQuestions(String slug) {
        if (!categoryRepository.existsBySlug(slug)) {
            throw new CategoryNotFoundException(slug);
        }
        return questionRepository.findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(slug, SOLVABLE_TYPES).stream()
                .map(QuestionResponse::from)
                .toList();
    }

    /** 회원(userId가 null이 아님)이면 채점 결과를 풀이 기록으로 저장한다. 채점이 실패하면 저장하지 않는다. */
    @Transactional
    public AnswerResponse submitAnswer(long questionId, long choiceId, Long userId) {
        Question question = questionRepository
                .findWithChoicesById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        GradingResult result = question.grade(choiceId);
        boolean recorded = userId != null;
        if (recorded) {
            questionAttemptRepository.save(
                    QuestionAttempt.graded(userRepository.getReferenceById(userId), question, result.correct()));
        }
        return AnswerResponse.from(result, recorded);
    }

    @Transactional
    public void selfGrade(long questionId, boolean correct, long userId) {
        Question question =
                questionRepository.findById(questionId).orElseThrow(() -> new QuestionNotFoundException(questionId));
        questionAttemptRepository.save(
                QuestionAttempt.selfGraded(userRepository.getReferenceById(userId), question, correct));
    }

    public TextAnswerResponse submitTextAnswer(long questionId) {
        Question question =
                questionRepository.findById(questionId).orElseThrow(() -> new QuestionNotFoundException(questionId));
        return TextAnswerResponse.from(question.revealModelAnswer());
    }
}
