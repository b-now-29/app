package com.b.now._29.app.quiz.question;

import jakarta.persistence.*;

@Entity
public class Choice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Question question;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "is_correct")
    private boolean correct;

    private int sortOrder;

    protected Choice() {
        // JPA 전용
    }

    Choice(Question question, String content, boolean correct, int sortOrder) {
        this.question = question;
        this.content = content;
        this.correct = correct;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public boolean isCorrect() {
        return correct;
    }
}
