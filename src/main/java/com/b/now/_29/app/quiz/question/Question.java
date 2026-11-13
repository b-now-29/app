package com.b.now._29.app.quiz.question;

import com.b.now._29.app.quiz.category.Category;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Question {

    private static final int CHOICE_SORT_STEP = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @Enumerated(EnumType.STRING)
    private QuestionType type;

    @Column(columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String modelAnswer;

    @Column(columnDefinition = "text")
    private String explanation;

    private int sortOrder;

    /** soft delete 시각. null이면 삭제되지 않은 문제다. */
    private Instant deletedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<Choice> choices = new ArrayList<>();

    protected Question() {
        // JPA 전용
    }

    public Question(Category category, QuestionType type, String content, String explanation, int sortOrder) {
        this.category = category;
        this.type = type;
        this.content = content;
        this.explanation = explanation;
        this.sortOrder = sortOrder;
    }

    /** 유형별 규칙을 지키는지 확인하고 만든다. 어기면 {@link InvalidQuestionException}. */
    public static Question create(Category category, QuestionDraft draft) {
        validate(draft);
        Question question =
                new Question(category, draft.type(), draft.content(), draft.explanation(), draft.sortOrder());
        question.modelAnswer = modelAnswerOf(draft);
        for (int i = 0; i < draft.choices().size(); i++) {
            ChoiceDraft choice = draft.choices().get(i);
            question.addChoice(choice.content(), choice.correct(), (i + 1) * CHOICE_SORT_STEP);
        }
        return question;
    }

    /** 검증을 먼저 하고 통과하면 바꾼다. 어기면 아무것도 바꾸지 않는다. 보기는 통째로 교체한다. */
    public void update(Category category, QuestionDraft draft) {
        validate(draft);
        this.category = category;
        this.type = draft.type();
        this.content = draft.content();
        this.explanation = draft.explanation();
        this.modelAnswer = modelAnswerOf(draft);
        this.sortOrder = draft.sortOrder();
        choices.clear();
        for (int i = 0; i < draft.choices().size(); i++) {
            ChoiceDraft choice = draft.choices().get(i);
            addChoice(choice.content(), choice.correct(), (i + 1) * CHOICE_SORT_STEP);
        }
    }

    /** 풀이 기록이 있는 문제는 유형을 바꿀 수 없다 (D17). 같은 유형이면 통과한다. */
    public void requireTypeChangeAllowed(QuestionType newType, boolean attempted) {
        if (attempted && newType != type) {
            throw new QuestionTypeChangeNotAllowedException(type, newType);
        }
    }

    /** 보기를 모두 비운다 (orphanRemoval로 DELETE 대상이 된다). 보기 교체 순서는 서비스가 정한다 (D16). */
    public void clearChoices() {
        choices.clear();
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 유형별 규칙은 여기 한 곳에 둔다. */
    private static void validate(QuestionDraft draft) {
        switch (draft.type()) {
            case MULTIPLE_CHOICE -> {
                if (draft.choices().size() < 2) {
                    throw new InvalidQuestionException("객관식은 보기가 2개 이상이어야 합니다.");
                }
                if (draft.choices().stream().filter(ChoiceDraft::correct).count() != 1) {
                    throw new InvalidQuestionException("객관식은 정답 보기가 정확히 1개여야 합니다.");
                }
                if (draft.modelAnswer() != null && !draft.modelAnswer().isBlank()) {
                    throw new InvalidQuestionException("객관식에는 모범 답안을 둘 수 없습니다.");
                }
            }
            case SHORT_ANSWER, ESSAY -> {
                if (draft.modelAnswer() == null || draft.modelAnswer().isBlank()) {
                    throw new InvalidQuestionException("주관식·서술형은 모범 답안이 필요합니다.");
                }
                if (!draft.choices().isEmpty()) {
                    throw new InvalidQuestionException("주관식·서술형에는 보기를 둘 수 없습니다.");
                }
            }
        }
    }

    private static String modelAnswerOf(QuestionDraft draft) {
        return draft.type() == QuestionType.MULTIPLE_CHOICE ? null : draft.modelAnswer();
    }

    public Choice addChoice(String content, boolean correct, int sortOrder) {
        Choice choice = new Choice(this, content, correct, sortOrder);
        choices.add(choice);
        return choice;
    }

    public GradingResult grade(long choiceId) {
        Choice submittedChoice = findSubmittedChoice(choiceId);
        Choice correctChoice = findCorrectChoice();
        return new GradingResult(submittedChoice.isCorrect(), correctChoice.getId(), explanation);
    }

    public ModelAnswerResult revealModelAnswer() {
        if (type != QuestionType.MULTIPLE_CHOICE) {
            return new ModelAnswerResult(modelAnswer, explanation);
        }
        throw new UnsupportedAnswerTypeException(type);
    }

    /** 자기 채점할 수 있는 유형(주관식·서술형)인지 확인한다. 객관식이면 거부한다. */
    public void requireSelfGradable() {
        if (type == QuestionType.MULTIPLE_CHOICE) {
            throw new UnsupportedAnswerTypeException(type);
        }
    }

    private Choice findSubmittedChoice(long choiceId) {
        return choices.stream()
                .filter(choice -> choice.getId() == choiceId)
                .findFirst()
                .orElseThrow(() -> new InvalidChoiceException(choiceId));
    }

    private Choice findCorrectChoice() {
        return choices.stream().filter(Choice::isCorrect).findFirst().orElseThrow(IllegalStateException::new);
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public QuestionType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getModelAnswer() {
        return modelAnswer;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<Choice> getChoices() {
        return List.copyOf(choices);
    }
}
