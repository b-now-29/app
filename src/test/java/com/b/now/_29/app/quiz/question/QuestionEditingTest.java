package com.b.now._29.app.quiz.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.b.now._29.app.quiz.category.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 관리자 문제 등록·수정의 유형별 검증 (R5, D5, D6)과 soft delete (R6, D2).
 *
 * <p>구현할 도메인 API (모두 {@code quiz.question} 패키지):
 *
 * <ul>
 *   <li>{@code record ChoiceDraft(String content, boolean correct)}
 *   <li>{@code record QuestionDraft(QuestionType type, String content, String explanation, String modelAnswer, int
 *       sortOrder, List<ChoiceDraft> choices)}
 *   <li>{@code static Question Question.create(Category category, QuestionDraft draft)} — 규칙을 어기면 {@code
 *       InvalidQuestionException}
 *   <li>{@code void Question.update(Category category, QuestionDraft draft)} — 검증을 먼저 하고, 실패하면 아무것도 바꾸지 않는다. 보기는
 *       통째로 교체한다
 *   <li>{@code void Question.delete()}, {@code boolean Question.isDeleted()}
 *   <li>getter: {@code getExplanation()}, {@code getModelAnswer()}, {@code getSortOrder()}
 *   <li>{@code InvalidQuestionException(String message)} — 400으로 응답하고 message를 detail로 보여 준다
 * </ul>
 */
class QuestionEditingTest {

    private static final Category JAVA = new Category("java", "Java", 10);
    private static final Category SPRING = new Category("spring", "Spring", 20);
    private static final String CONTENT = "문자열 내용을 비교할 때 쓰는 것은?";
    private static final String EXPLANATION = "equals()는 문자열 내용을 비교한다.";
    private static final String MODEL_ANSWER = "문자열 내용은 equals()로 비교한다.";

    // 객관식 생성

    @Test
    @DisplayName("보기 2개와 정답 1개인 객관식을 만든다. 보기는 받은 순서를 지킨다 (R5)")
    void createsMultipleChoice() {
        Question question = Question.create(
                JAVA, multipleChoice(List.of(new ChoiceDraft("==", false), new ChoiceDraft("equals()", true))));

        assertThat(question.getCategory()).isSameAs(JAVA);
        assertThat(question.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(question.getContent()).isEqualTo(CONTENT);
        assertThat(question.getExplanation()).isEqualTo(EXPLANATION);
        assertThat(question.getModelAnswer()).isNull();
        assertThat(question.getSortOrder()).isEqualTo(30);
        assertThat(question.getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("==", false), tuple("equals()", true));
        assertThat(question.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("해설 없이도 객관식을 만들 수 있다")
    void createsMultipleChoiceWithoutExplanation() {
        Question question = Question.create(
                JAVA,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        CONTENT,
                        null,
                        null,
                        0,
                        List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        assertThat(question.getExplanation()).isNull();
    }

    @Test
    @DisplayName("보기가 여러 개여도 정답이 1개면 만든다 (R5)")
    void createsMultipleChoiceWithManyChoices() {
        Question question = Question.create(
                JAVA,
                multipleChoice(List.of(
                        new ChoiceDraft("A", false),
                        new ChoiceDraft("B", false),
                        new ChoiceDraft("C", true),
                        new ChoiceDraft("D", false))));

        assertThat(question.getChoices()).extracting(Choice::getContent).containsExactly("A", "B", "C", "D");
    }

    @Test
    @DisplayName("보기가 없는 객관식은 InvalidQuestionException이다 (R5)")
    void rejectsMultipleChoiceWithoutChoices() {
        assertRejected(multipleChoice(List.of()));
    }

    @Test
    @DisplayName("보기가 1개인 객관식은 InvalidQuestionException이다 (R5)")
    void rejectsMultipleChoiceWithOneChoice() {
        assertRejected(multipleChoice(List.of(new ChoiceDraft("A", true))));
    }

    @Test
    @DisplayName("정답이 없는 객관식은 InvalidQuestionException이다 (R5)")
    void rejectsMultipleChoiceWithoutCorrectChoice() {
        assertRejected(multipleChoice(List.of(new ChoiceDraft("A", false), new ChoiceDraft("B", false))));
    }

    @Test
    @DisplayName("정답이 2개인 객관식은 InvalidQuestionException이다 (R5)")
    void rejectsMultipleChoiceWithTwoCorrectChoices() {
        assertRejected(multipleChoice(
                List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", true), new ChoiceDraft("C", false))));
    }

    @Test
    @DisplayName("모범 답안이 있는 객관식은 InvalidQuestionException이다 (R5)")
    void rejectsMultipleChoiceWithModelAnswer() {
        assertRejected(new QuestionDraft(
                QuestionType.MULTIPLE_CHOICE,
                CONTENT,
                EXPLANATION,
                MODEL_ANSWER,
                30,
                List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));
    }

    @Test
    @DisplayName("검증 실패 메시지는 화면에 보여 줄 수 있도록 비어 있지 않다 (화면 흐름: 400 서버 메시지)")
    void rejectionHasMessage() {
        assertThatThrownBy(() -> Question.create(JAVA, multipleChoice(List.of())))
                .isInstanceOf(InvalidQuestionException.class)
                .message()
                .isNotBlank();
    }

    // 주관식·서술형 생성

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("모범 답안이 있고 보기가 없는 주관식·서술형을 만든다 (R5)")
    void createsTextQuestion(QuestionType type) {
        Question question = Question.create(SPRING, textQuestion(type, MODEL_ANSWER, List.of()));

        assertThat(question.getType()).isEqualTo(type);
        assertThat(question.getCategory()).isSameAs(SPRING);
        assertThat(question.getModelAnswer()).isEqualTo(MODEL_ANSWER);
        assertThat(question.getChoices()).isEmpty();
        // 공개 흐름의 모범 답안 공개도 그대로 동작한다
        assertThat(question.revealModelAnswer()).isEqualTo(new ModelAnswerResult(MODEL_ANSWER, EXPLANATION));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t "})
    @DisplayName("모범 답안이 없거나 공백뿐인 주관식은 InvalidQuestionException이다 (R5)")
    void rejectsShortAnswerWithoutModelAnswer(String modelAnswer) {
        assertRejected(textQuestion(QuestionType.SHORT_ANSWER, modelAnswer, List.of()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t "})
    @DisplayName("모범 답안이 없거나 공백뿐인 서술형은 InvalidQuestionException이다 (R5)")
    void rejectsEssayWithoutModelAnswer(String modelAnswer) {
        assertRejected(textQuestion(QuestionType.ESSAY, modelAnswer, List.of()));
    }

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("보기가 있는 주관식·서술형은 InvalidQuestionException이다 (R5)")
    void rejectsTextQuestionWithChoices(QuestionType type) {
        assertRejected(
                textQuestion(type, MODEL_ANSWER, List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));
    }

    // 수정

    @Test
    @DisplayName("수정하면 카테고리, 내용, 해설, 표시 순서를 바꾸고 보기를 통째로 교체한다 (D6)")
    void updatesAndReplacesChoices() {
        Question question = Question.create(
                JAVA, multipleChoice(List.of(new ChoiceDraft("옛 보기 1", true), new ChoiceDraft("옛 보기 2", false))));

        question.update(
                SPRING,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        "바뀐 문제",
                        "바뀐 해설",
                        null,
                        99,
                        List.of(
                                new ChoiceDraft("새 보기 1", false),
                                new ChoiceDraft("새 보기 2", false),
                                new ChoiceDraft("새 보기 3", true))));

        assertThat(question.getCategory()).isSameAs(SPRING);
        assertThat(question.getContent()).isEqualTo("바뀐 문제");
        assertThat(question.getExplanation()).isEqualTo("바뀐 해설");
        assertThat(question.getSortOrder()).isEqualTo(99);
        assertThat(question.getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("새 보기 1", false), tuple("새 보기 2", false), tuple("새 보기 3", true));
    }

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("객관식을 주관식·서술형으로 바꾸면 보기가 없어지고 모범 답안이 생긴다 (D6)")
    void changesMultipleChoiceToTextQuestion(QuestionType type) {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        question.update(JAVA, textQuestion(type, MODEL_ANSWER, List.of()));

        assertThat(question.getType()).isEqualTo(type);
        assertThat(question.getChoices()).isEmpty();
        assertThat(question.getModelAnswer()).isEqualTo(MODEL_ANSWER);
    }

    @Test
    @DisplayName("주관식을 객관식으로 바꾸면 모범 답안이 없어지고 보기가 생긴다 (D6)")
    void changesTextQuestionToMultipleChoice() {
        Question question = Question.create(JAVA, textQuestion(QuestionType.SHORT_ANSWER, MODEL_ANSWER, List.of()));

        question.update(JAVA, multipleChoice(List.of(new ChoiceDraft("A", false), new ChoiceDraft("B", true))));

        assertThat(question.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(question.getModelAnswer()).isNull();
        assertThat(question.getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("A", false), tuple("B", true));
    }

    @Test
    @DisplayName("규칙을 어긴 수정은 InvalidQuestionException이고 문제를 조금도 바꾸지 않는다 (R5, D5)")
    void rejectedUpdateLeavesQuestionUnchanged() {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        assertThatThrownBy(() -> question.update(
                        SPRING,
                        new QuestionDraft(
                                QuestionType.MULTIPLE_CHOICE,
                                "바뀐 문제",
                                "바뀐 해설",
                                null,
                                99,
                                List.of(new ChoiceDraft("하나뿐인 보기", true)))))
                .isInstanceOf(InvalidQuestionException.class);

        assertThat(question.getCategory()).isSameAs(JAVA);
        assertThat(question.getContent()).isEqualTo(CONTENT);
        assertThat(question.getExplanation()).isEqualTo(EXPLANATION);
        assertThat(question.getSortOrder()).isEqualTo(30);
        assertThat(question.getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("A", true), tuple("B", false));
    }

    @Test
    @DisplayName("모범 답안 없이 주관식으로 바꾸는 수정은 InvalidQuestionException이고 유형도 그대로다 (R5, D6)")
    void rejectsTypeChangeWithoutModelAnswer() {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        assertThatThrownBy(() -> question.update(JAVA, textQuestion(QuestionType.SHORT_ANSWER, " ", List.of())))
                .isInstanceOf(InvalidQuestionException.class);

        assertThat(question.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(question.getChoices()).hasSize(2);
        assertThat(question.getModelAnswer()).isNull();
    }

    // 풀이 기록이 있는 문제의 유형 변경 (D17)
    //
    // 구현할 API: void Question.requireTypeChangeAllowed(QuestionType newType, boolean attempted)
    //   풀이 기록이 있고(attempted) 유형이 바뀌면 QuestionTypeChangeNotAllowedException(QuestionType from, QuestionType to)
    //   (quiz.question, RuntimeException → 409). 서비스는 clearChoices() 전에 이 검사를 호출한다.
    //   메시지: "풀이 기록이 있는 문제는 유형을 바꿀 수 없습니다."

    @ParameterizedTest
    @EnumSource(
            value = QuestionType.class,
            names = {"SHORT_ANSWER", "ESSAY"})
    @DisplayName("풀이 기록이 있는 객관식은 다른 유형으로 바꿀 수 없고 문제는 그대로다 (D17)")
    void rejectsTypeChangeOfAttemptedQuestion(QuestionType newType) {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        assertThatThrownBy(() -> question.requireTypeChangeAllowed(newType, true))
                .isInstanceOf(QuestionTypeChangeNotAllowedException.class)
                .hasMessage("풀이 기록이 있는 문제는 유형을 바꿀 수 없습니다.");

        assertThat(question.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(question.getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("A", true), tuple("B", false));
    }

    @Test
    @DisplayName("풀이 기록이 있는 서술형도 주관식으로 바꿀 수 없다 (D17)")
    void rejectsTypeChangeBetweenTextTypes() {
        Question question = Question.create(JAVA, textQuestion(QuestionType.ESSAY, MODEL_ANSWER, List.of()));

        assertThatThrownBy(() -> question.requireTypeChangeAllowed(QuestionType.SHORT_ANSWER, true))
                .isInstanceOf(QuestionTypeChangeNotAllowedException.class);

        assertThat(question.getType()).isEqualTo(QuestionType.ESSAY);
        assertThat(question.getModelAnswer()).isEqualTo(MODEL_ANSWER);
    }

    @Test
    @DisplayName("풀이 기록이 있어도 유형이 같으면 내용과 보기를 바꿀 수 있다 (D17)")
    void allowsSameTypeUpdateOfAttemptedQuestion() {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        question.requireTypeChangeAllowed(QuestionType.MULTIPLE_CHOICE, true);
        question.update(
                JAVA,
                new QuestionDraft(
                        QuestionType.MULTIPLE_CHOICE,
                        "바뀐 문제",
                        null,
                        null,
                        30,
                        List.of(new ChoiceDraft("새 A", false), new ChoiceDraft("새 B", true))));

        assertThat(question.getContent()).isEqualTo("바뀐 문제");
        assertThat(question.getChoices())
                .extracting(Choice::getContent, Choice::isCorrect)
                .containsExactly(tuple("새 A", false), tuple("새 B", true));
    }

    @Test
    @DisplayName("풀이 기록이 없으면 유형을 바꿀 수 있다 (D6, D17)")
    void allowsTypeChangeWithoutAttempts() {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        question.requireTypeChangeAllowed(QuestionType.ESSAY, false);
        question.update(JAVA, textQuestion(QuestionType.ESSAY, MODEL_ANSWER, List.of()));

        assertThat(question.getType()).isEqualTo(QuestionType.ESSAY);
    }

    // soft delete

    @Test
    @DisplayName("삭제하면 삭제 상태가 된다 (R6, D2)")
    void deletesSoftly() {
        Question question =
                Question.create(JAVA, multipleChoice(List.of(new ChoiceDraft("A", true), new ChoiceDraft("B", false))));

        question.delete();

        assertThat(question.isDeleted()).isTrue();
        // 풀이 기록이 참조하므로 내용은 그대로 남는다
        assertThat(question.getContent()).isEqualTo(CONTENT);
    }

    private static void assertRejected(QuestionDraft draft) {
        assertThatThrownBy(() -> Question.create(JAVA, draft)).isInstanceOf(InvalidQuestionException.class);
    }

    private static QuestionDraft multipleChoice(List<ChoiceDraft> choices) {
        return new QuestionDraft(QuestionType.MULTIPLE_CHOICE, CONTENT, EXPLANATION, null, 30, choices);
    }

    private static QuestionDraft textQuestion(QuestionType type, String modelAnswer, List<ChoiceDraft> choices) {
        return new QuestionDraft(type, CONTENT, EXPLANATION, modelAnswer, 30, choices);
    }
}
