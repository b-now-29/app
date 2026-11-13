package com.b.now._29.app.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.quiz.category.Category;
import com.b.now._29.app.quiz.question.Question;
import com.b.now._29.app.quiz.question.QuestionType;
import com.b.now._29.app.quiz.question.UnsupportedAnswerTypeException;
import com.b.now._29.app.user.Nickname;
import com.b.now._29.app.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class QuestionAttemptTest {

    private static final String MODEL_ANSWER = "문자열 내용은 equals()로 비교한다.";

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("주관식·서술형은 자기 채점한 맞음을 기록으로 만든다 (R2)")
    void selfGradesCorrect(QuestionType type) {
        User user = user();
        Question question = question(type);

        QuestionAttempt attempt = QuestionAttempt.selfGraded(user, question, true);

        assertThat(attempt.getUser()).isSameAs(user);
        assertThat(attempt.getQuestion()).isSameAs(question);
        assertThat(attempt.isCorrect()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("주관식·서술형은 자기 채점한 틀림을 기록으로 만든다 (R2)")
    void selfGradesWrong(QuestionType type) {
        QuestionAttempt attempt = QuestionAttempt.selfGraded(user(), question(type), false);

        assertThat(attempt.isCorrect()).isFalse();
    }

    @Test
    @DisplayName("객관식 문제를 자기 채점하면 revealModelAnswer()처럼 UnsupportedAnswerTypeException을 던진다")
    void rejectsSelfGradeOfMultipleChoice() {
        Question question = question(QuestionType.MULTIPLE_CHOICE);

        assertThatThrownBy(() -> QuestionAttempt.selfGraded(user(), question, true))
                .isInstanceOf(UnsupportedAnswerTypeException.class);
    }

    // DB 제약은 객관식의 model_answer를 막지 않는다. 값의 유무가 아니라 유형으로 판단하는지 확인한다
    @Test
    @DisplayName("모범 답안 값이 있어도 객관식 문제면 자기 채점을 거부한다")
    void rejectsSelfGradeOfMultipleChoiceWithModelAnswer() {
        Question question = question(QuestionType.MULTIPLE_CHOICE);
        ReflectionTestUtils.setField(question, "modelAnswer", MODEL_ANSWER);

        assertThatThrownBy(() -> QuestionAttempt.selfGraded(user(), question, false))
                .isInstanceOf(UnsupportedAnswerTypeException.class);
    }

    @Test
    @DisplayName("객관식 채점 결과는 그대로 기록으로 만든다 (R1)")
    void recordsGradingResultOfMultipleChoice() {
        User user = user();
        Question question = question(QuestionType.MULTIPLE_CHOICE);

        QuestionAttempt attempt = QuestionAttempt.graded(user, question, true);

        assertThat(attempt.getUser()).isSameAs(user);
        assertThat(attempt.getQuestion()).isSameAs(question);
        assertThat(attempt.isCorrect()).isTrue();
    }

    private static User user() {
        return new User("user@example.com", "$2a$10$hash", new Nickname("회원"), 1);
    }

    /** 모범 답안은 DB 시드로만 들어가고 생성자가 받지 않으므로 필드에 직접 채운다. */
    private static Question question(QuestionType type) {
        Question question = new Question(new Category("java", "Java", 10), type, "문제", null, 10);
        if (type != QuestionType.MULTIPLE_CHOICE) {
            ReflectionTestUtils.setField(question, "modelAnswer", MODEL_ANSWER);
        }
        return question;
    }
}
