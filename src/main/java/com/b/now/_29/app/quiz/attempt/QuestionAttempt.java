package com.b.now._29.app.quiz.attempt;

import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
public class QuestionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    private boolean correct;

    /** DB 기본값 now()로 채워진다. */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Instant attemptedAt;

    protected QuestionAttempt() {
        // JPA 전용
    }

    private QuestionAttempt(User user, Question question, boolean correct) {
        this.user = user;
        this.question = question;
        this.correct = correct;
    }

    /** 서버가 채점한 결과(객관식)를 기록으로 만든다. */
    public static QuestionAttempt graded(User user, Question question, boolean correct) {
        return new QuestionAttempt(user, question, correct);
    }

    /** 주관식·서술형의 자기 채점 결과를 기록으로 만든다. 객관식은 거부한다. */
    public static QuestionAttempt selfGraded(User user, Question question, boolean correct) {
        question.requireSelfGradable();
        return new QuestionAttempt(user, question, correct);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Question getQuestion() {
        return question;
    }

    public boolean isCorrect() {
        return correct;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
