package com.b.now._29.app.quiz.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b.now._29.app.quiz.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class QuestionTest {

    private static final String EXPLANATION = "equals()는 문자열 내용을 비교한다.";
    private static final String MODEL_ANSWER = "문자열 내용은 equals()로 비교한다.";

    // Long 캐시(-128~127) 밖의 값을 써서 Long 참조 비교(==) 버그도 드러나게 한다
    private static final long FIRST_WRONG_CHOICE_ID = 1001L;
    private static final long CORRECT_CHOICE_ID = 1002L;
    private static final long LAST_WRONG_CHOICE_ID = 1003L;
    private static final long OTHER_QUESTION_CHOICE_ID = 2001L;

    @Test
    @DisplayName("정답 보기를 고르면 정답으로 채점하고 해설을 돌려준다")
    void gradesCorrectChoice() {
        Question question = questionWithCorrectChoice(EXPLANATION);

        GradingResult result = question.grade(CORRECT_CHOICE_ID);

        assertThat(result).isEqualTo(new GradingResult(true, CORRECT_CHOICE_ID, EXPLANATION));
    }

    @Test
    @DisplayName("오답 보기를 고르면 오답으로 채점하고 고른 보기가 아닌 정답 보기 ID와 해설을 돌려준다")
    void gradesWrongChoice() {
        Question question = questionWithCorrectChoice(EXPLANATION);

        GradingResult result = question.grade(FIRST_WRONG_CHOICE_ID);

        assertThat(result).isEqualTo(new GradingResult(false, CORRECT_CHOICE_ID, EXPLANATION));
    }

    @Test
    @DisplayName("정답 보기 뒤에 있는 오답 보기도 오답으로 채점한다")
    void gradesWrongChoiceAfterCorrectChoice() {
        Question question = questionWithCorrectChoice(EXPLANATION);

        GradingResult result = question.grade(LAST_WRONG_CHOICE_ID);

        assertThat(result).isEqualTo(new GradingResult(false, CORRECT_CHOICE_ID, EXPLANATION));
    }

    @Test
    @DisplayName("해설이 없는 문제는 해설을 null로 돌려준다")
    void gradesQuestionWithoutExplanation() {
        Question question = questionWithCorrectChoice(null);

        GradingResult result = question.grade(CORRECT_CHOICE_ID);

        assertThat(result).isEqualTo(new GradingResult(true, CORRECT_CHOICE_ID, null));
    }

    @Test
    @DisplayName("다른 문제의 보기를 고르면 InvalidChoiceException을 던진다")
    void rejectsChoiceOfOtherQuestion() {
        Question question = questionWithCorrectChoice(EXPLANATION);
        Question otherQuestion = question(EXPLANATION);
        addChoice(otherQuestion, "다른 문제의 보기", true, 10, OTHER_QUESTION_CHOICE_ID);

        assertThatThrownBy(() -> question.grade(OTHER_QUESTION_CHOICE_ID)).isInstanceOf(InvalidChoiceException.class);
    }

    @Test
    @DisplayName("정답 보기가 없는 데이터는 사용자 입력 오류가 아니라 IllegalStateException으로 알린다")
    void failsWhenQuestionHasNoCorrectChoice() {
        Question question = question(EXPLANATION);
        addChoice(question, "오답 1", false, 10, FIRST_WRONG_CHOICE_ID);
        addChoice(question, "오답 2", false, 20, LAST_WRONG_CHOICE_ID);

        assertThatThrownBy(() -> question.grade(FIRST_WRONG_CHOICE_ID))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(InvalidChoiceException.class);
    }

    // 고른 보기를 먼저 검사해야 보기가 없는 주관식 문제의 POST가 500이 아닌 400이 된다
    @Test
    @DisplayName("보기가 없는 문제를 채점하면 정답 보기가 없다는 오류보다 InvalidChoiceException을 먼저 던진다")
    void rejectsChoiceWhenQuestionHasNoChoices() {
        Question question = question(EXPLANATION);

        assertThatThrownBy(() -> question.grade(CORRECT_CHOICE_ID)).isInstanceOf(InvalidChoiceException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("객관식이 아닌 문제는 모범 답안과 해설을 돌려준다 (D4)")
    void revealsModelAnswer(QuestionType type) {
        Question question = questionWithModelAnswer(type, EXPLANATION);

        ModelAnswerResult result = question.revealModelAnswer();

        assertThat(result).isEqualTo(new ModelAnswerResult(MODEL_ANSWER, EXPLANATION));
    }

    @Test
    @DisplayName("해설이 없는 문제는 모범 답안과 null 해설을 돌려준다")
    void revealsModelAnswerWithoutExplanation() {
        Question question = questionWithModelAnswer(QuestionType.SHORT_ANSWER, null);

        ModelAnswerResult result = question.revealModelAnswer();

        assertThat(result).isEqualTo(new ModelAnswerResult(MODEL_ANSWER, null));
    }

    @Test
    @DisplayName("객관식 문제에 모범 답안을 요청하면 UnsupportedAnswerTypeException을 던진다 (D4)")
    void rejectsRevealOfMultipleChoice() {
        Question question = questionWithCorrectChoice(EXPLANATION);

        assertThatThrownBy(question::revealModelAnswer).isInstanceOf(UnsupportedAnswerTypeException.class);
    }

    // DB 제약은 객관식의 model_answer를 막지 않는다. 값의 유무가 아니라 유형으로 판단하는지 확인한다
    @Test
    @DisplayName("모범 답안 값이 있어도 객관식 문제면 UnsupportedAnswerTypeException을 던진다 (D4)")
    void rejectsRevealOfMultipleChoiceWithModelAnswer() {
        Question question = questionWithModelAnswer(QuestionType.MULTIPLE_CHOICE, EXPLANATION);

        assertThatThrownBy(question::revealModelAnswer).isInstanceOf(UnsupportedAnswerTypeException.class);
    }

    /**
     * 모범 답안은 DB 시드로만 들어가고 생성자가 받지 않으므로 필드에 직접 채운다.
     */
    private static Question questionWithModelAnswer(QuestionType type, String explanation) {
        Category category = new Category("java", "Java", 10);
        Question question = new Question(category, type, "문자열 내용은 어떻게 비교하는가?", explanation, 10);
        ReflectionTestUtils.setField(question, "modelAnswer", MODEL_ANSWER);
        return question;
    }

    private static Question questionWithCorrectChoice(String explanation) {
        Question question = question(explanation);
        addChoice(question, "==", false, 10, FIRST_WRONG_CHOICE_ID);
        addChoice(question, "equals()", true, 20, CORRECT_CHOICE_ID);
        addChoice(question, "hashCode()", false, 30, LAST_WRONG_CHOICE_ID);
        return question;
    }

    private static Question question(String explanation) {
        Category category = new Category("java", "Java", 10);
        return new Question(category, QuestionType.MULTIPLE_CHOICE, "문자열 내용을 비교할 때 쓰는 것은?", explanation, 10);
    }

    /**
     * 보기 ID는 DB가 만들지만 이 테스트는 DB 없이 돌기 때문에 필드에 직접 채운다.
     */
    private static void addChoice(Question question, String content, boolean correct, int sortOrder, long id) {
        Choice choice = question.addChoice(content, correct, sortOrder);
        ReflectionTestUtils.setField(choice, "id", id);
    }
}
