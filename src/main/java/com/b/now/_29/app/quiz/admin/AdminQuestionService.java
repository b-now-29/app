package com.b.now._29.app.quiz.admin;

import com.b.now._29.app.quiz.attempt.QuestionAttemptRepository;
import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionNotFoundException;
import com.b.now._29.app.quiz.question.QuestionRepository;
import com.b.now._29.app.quiz.question.QuestionType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminQuestionService {

    private static final List<QuestionType> ALL_TYPES = List.of(QuestionType.values());

    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;

    private final QuestionAttemptRepository questionAttemptRepository;
    private final AdminScopeService adminScopeService;

    public AdminQuestionService(
            QuestionRepository questionRepository,
            CategoryRepository categoryRepository,
            QuestionAttemptRepository questionAttemptRepository,
            AdminScopeService adminScopeService) {
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.questionAttemptRepository = questionAttemptRepository;
        this.adminScopeService = adminScopeService;
    }

    public List<AdminQuestionResponse> getQuestions(long actorId, String categorySlug) {
        AdminScope scope = adminScopeService.of(actorId);
        Category category = categoryRepository
                .findBySlugAndDeletedAtIsNull(categorySlug)
                .orElseThrow(() -> new CategoryNotFoundException(categorySlug));
        scope.requireManage(category);
        return questionRepository
                .findAllByCategorySlugAndTypeInOrderBySortOrderAscIdAsc(categorySlug, ALL_TYPES)
                .stream()
                .map(AdminQuestionResponse::from)
                .toList();
    }

    public AdminQuestionResponse getQuestion(long actorId, long id) {
        AdminScope scope = adminScopeService.of(actorId);
        Question question = findWithChoices(id);
        scope.requireManage(question.getCategory());
        return AdminQuestionResponse.from(question);
    }

    @Transactional
    public AdminQuestionResponse create(long actorId, QuestionRequest request) {
        AdminScope scope = adminScopeService.of(actorId);
        Category category = findCategory(request.categorySlug());
        scope.requireManage(category);
        Question question = Question.create(category, request.toDraft());
        return AdminQuestionResponse.from(questionRepository.saveAndFlush(question));
    }

    /**
     * 보기는 통째로 바뀌므로 flush로 새 보기의 id를 받은 뒤 응답을 만든다. 문제를 옮길 때는 원래 카테고리와 옮겨 갈 카테고리를 모두 담당해야 한다
     * (R5).
     */
    @Transactional
    public AdminQuestionResponse update(long actorId, long id, QuestionRequest request) {
        AdminScope scope = adminScopeService.of(actorId);
        Question question = findWithChoices(id);
        scope.requireManage(question.getCategory());
        Category category = findCategory(request.categorySlug());
        scope.requireManage(category);
        // 유형 변경 검사는 보기를 비우거나 flush하기 전에 한다 (D17)
        question.requireTypeChangeAllowed(request.type(), questionAttemptRepository.existsByQuestionId(id));
        // 옛 보기 DELETE를 먼저 DB에 보낸 뒤 새 보기를 INSERT한다. Hibernate 실행 순서에 기대면 INSERT가 앞서
        // uq_choice_one_correct를 어긴다 (D16). 검증이 실패하면 트랜잭션 롤백으로 DELETE도 취소된다.
        question.clearChoices();
        questionRepository.flush();
        question.update(category, request.toDraft());
        return AdminQuestionResponse.from(questionRepository.saveAndFlush(question));
    }

    @Transactional
    public void delete(long actorId, long id) {
        AdminScope scope = adminScopeService.of(actorId);
        Question question =
                questionRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new QuestionNotFoundException(id));
        scope.requireManage(question.getCategory());
        question.delete();
    }

    private Question findWithChoices(long id) {
        return questionRepository.findWithChoicesById(id).orElseThrow(() -> new QuestionNotFoundException(id));
    }

    /** 카테고리 삭제와 직렬화하려고 FOR SHARE로 잠근다. */
    private Category findCategory(String slug) {
        return categoryRepository
                .findWithShareLockBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
    }
}
