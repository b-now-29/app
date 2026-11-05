package com.b.now._29.app.quiz.question;

import com.b.now._29.app.quiz.category.Category;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Question {

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

    public QuestionType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public List<Choice> getChoices() {
        return List.copyOf(choices);
    }
}
