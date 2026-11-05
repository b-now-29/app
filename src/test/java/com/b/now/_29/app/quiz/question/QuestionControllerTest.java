package com.b.now._29.app.quiz.question;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(QuestionController.class)
@AutoConfigureRestDocs
class QuestionControllerTest {

    private static final String SLUG = "java";
    private static final String QUESTION_CONTENT = "문자열 내용을 비교할 때 쓰는 것은?";
    private static final String EXPLANATION = "equals()는 문자열 내용을 비교한다.";
    private static final long QUESTION_ID = 1L;
    private static final long WRONG_CHOICE_ID = 10L;
    private static final long CORRECT_CHOICE_ID = 11L;
    private static final long SHORT_ANSWER_QUESTION_ID = 2L;
    private static final String SHORT_ANSWER_CONTENT = "문자열 내용은 어떻게 비교하는가?";
    private static final long ESSAY_QUESTION_ID = 3L;
    private static final String ESSAY_CONTENT = "equals()와 ==를 비교해 서술하세요.";
    private static final String MODEL_ANSWER = "문자열 내용은 equals()로 비교한다.";

    // 디코딩 방식과 상관없이 응답에서 찾을 수 있도록 ASCII로만 쓴다
    private static final String INTERNAL_MESSAGE = "question 42 has no correct choice in table choice";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QuestionService questionService;

    @Test
    @DisplayName("문제 목록은 id, type, content, choices만 내려주고 정답 정보, 모범 답안, 해설은 내려주지 않는다 (R2)")
    void getQuestions() throws Exception {
        given(questionService.getQuestions(SLUG))
                .willReturn(List.of(
                        new QuestionResponse(
                                QUESTION_ID,
                                QuestionType.MULTIPLE_CHOICE,
                                QUESTION_CONTENT,
                                List.of(
                                        new ChoiceResponse(WRONG_CHOICE_ID, "=="),
                                        new ChoiceResponse(CORRECT_CHOICE_ID, "equals()"))),
                        new QuestionResponse(
                                SHORT_ANSWER_QUESTION_ID, QuestionType.SHORT_ANSWER, SHORT_ANSWER_CONTENT, List.of()),
                        new QuestionResponse(ESSAY_QUESTION_ID, QuestionType.ESSAY, ESSAY_CONTENT, List.of())));

        mvc.perform(get("/api/v1/categories/{slug}/questions", SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].keys()", containsInAnyOrder("id", "type", "content", "choices")))
                .andExpect(jsonPath("$[0].id").value(QUESTION_ID))
                .andExpect(jsonPath("$[0].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$[0].content").value(QUESTION_CONTENT))
                .andExpect(jsonPath("$[1].keys()", containsInAnyOrder("id", "type", "content", "choices")))
                .andExpect(jsonPath("$[1].id").value(SHORT_ANSWER_QUESTION_ID))
                .andExpect(jsonPath("$[1].type").value("SHORT_ANSWER"))
                .andExpect(jsonPath("$[1].content").value(SHORT_ANSWER_CONTENT))
                .andExpect(jsonPath("$[1].choices.length()").value(0))
                .andExpect(jsonPath("$[2].keys()", containsInAnyOrder("id", "type", "content", "choices")))
                .andExpect(jsonPath("$[2].id").value(ESSAY_QUESTION_ID))
                .andExpect(jsonPath("$[2].type").value("ESSAY"))
                .andExpect(jsonPath("$[2].content").value(ESSAY_CONTENT))
                .andExpect(jsonPath("$[2].choices.length()").value(0))
                .andExpect(jsonPath("$[0].choices.length()").value(2))
                .andExpect(jsonPath("$[0].choices[0].keys()", containsInAnyOrder("id", "content")))
                .andExpect(jsonPath("$[0].choices[0].id").value(WRONG_CHOICE_ID))
                .andExpect(jsonPath("$[0].choices[0].content").value("=="))
                .andExpect(jsonPath("$[0].choices[1].keys()", containsInAnyOrder("id", "content")))
                .andExpect(jsonPath("$[0].choices[1].id").value(CORRECT_CHOICE_ID))
                .andExpect(jsonPath("$[0].choices[1].content").value("equals()"))
                .andDo(document(
                        "questions-list",
                        pathParameters(parameterWithName("slug").description("카테고리 식별자")),
                        responseFields(
                                fieldWithPath("[].id").description("문제 ID"),
                                fieldWithPath("[].type").description("문제 유형 (MULTIPLE_CHOICE, SHORT_ANSWER, ESSAY)"),
                                fieldWithPath("[].content").description("문제 내용"),
                                fieldWithPath("[].choices").description("보기 목록 (주관식과 서술형은 빈 배열)"),
                                fieldWithPath("[].choices[].id").description("보기 ID"),
                                fieldWithPath("[].choices[].content").description("보기 내용"))));
    }

    @Test
    @DisplayName("문제가 없는 카테고리는 빈 배열을 반환한다")
    void getQuestionsReturnsEmptyArray() throws Exception {
        given(questionService.getQuestions(SLUG)).willReturn(List.of());

        mvc.perform(get("/api/v1/categories/{slug}/questions", SLUG))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("없는 slug는 404 ProblemDetail로 응답한다")
    void getQuestionsOfUnknownCategory() throws Exception {
        CategoryNotFoundException exception = new CategoryNotFoundException("nope");
        given(questionService.getQuestions("nope")).willThrow(exception);

        mvc.perform(get("/api/v1/categories/{slug}/questions", "nope"))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("questions-list-category-not-found"));
    }

    @Test
    @DisplayName("답을 제출하면 채점 결과를 응답한다")
    void submitAnswer() throws Exception {
        given(questionService.submitAnswer(QUESTION_ID, WRONG_CHOICE_ID))
                .willReturn(new AnswerResponse(false, CORRECT_CHOICE_ID, EXPLANATION));

        postAnswer(QUESTION_ID, answerBody(WRONG_CHOICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("correct", "correctChoiceId", "explanation")))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correctChoiceId").value(CORRECT_CHOICE_ID))
                .andExpect(jsonPath("$.explanation").value(EXPLANATION))
                .andDo(document(
                        "answers-submit",
                        pathParameters(parameterWithName("questionId").description("문제 ID")),
                        requestFields(fieldWithPath("choiceId").description("고른 보기의 ID")),
                        responseFields(
                                fieldWithPath("correct").description("정답 여부"),
                                fieldWithPath("correctChoiceId").description("정답 보기의 ID"),
                                fieldWithPath("explanation").description("해설 (없으면 null)"))));
    }

    @Test
    @DisplayName("해설이 없는 문제는 explanation 필드를 null로 응답한다")
    void submitAnswerWithoutExplanation() throws Exception {
        given(questionService.submitAnswer(QUESTION_ID, CORRECT_CHOICE_ID))
                .willReturn(new AnswerResponse(true, CORRECT_CHOICE_ID, null));

        postAnswer(QUESTION_ID, answerBody(CORRECT_CHOICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.correctChoiceId").value(CORRECT_CHOICE_ID))
                .andExpect(jsonPath("$.explanation").hasJsonPath())
                .andExpect(jsonPath("$.explanation").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choiceId\": null}", "{\"choiceId\": \"abc\"}", "{\"choiceId\": ", ""})
    @DisplayName("choiceId 누락, 형식 오류, 깨진 JSON, 빈 본문은 400 ProblemDetail이고 Service를 호출하지 않는다")
    void rejectsInvalidBody(String body) throws Exception {
        postAnswer(QUESTION_ID, body).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(questionService).shouldHaveNoInteractions();
    }

    /** 정수로 잘리면 사용자가 고르지 않은 보기가 조용히 채점된다. 소수 표기는 값과 상관없이 받지 않으므로 {@code 1.0}도 거부한다. */
    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1.0"})
    @DisplayName("choiceId가 소수 표기이면 값과 상관없이 400 ProblemDetail이고 Service를 호출하지 않는다 (D19)")
    void rejectsFractionalChoiceId(String choiceIdLiteral) throws Exception {
        ResultActions result = postAnswer(QUESTION_ID, "{\"choiceId\": " + choiceIdLiteral + "}");

        // 실패할 때 응답 상태와 Service가 받은 값을 함께 볼 수 있도록 assertAll로 묶는다
        assertAll(() -> result.andExpect(problemDetail(HttpStatus.BAD_REQUEST)), () -> then(questionService)
                .shouldHaveNoInteractions());
    }

    @Test
    @DisplayName("경로의 questionId가 숫자가 아니면 400 ProblemDetail이고 Service를 호출하지 않는다")
    void rejectsNonNumericQuestionId() throws Exception {
        postAnswer("abc", answerBody(CORRECT_CHOICE_ID)).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(questionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("그 문제의 보기가 아니면 400 ProblemDetail로 응답한다")
    void rejectsChoiceOfOtherQuestion() throws Exception {
        long otherChoiceId = 999L;
        InvalidChoiceException exception = new InvalidChoiceException(otherChoiceId);
        given(questionService.submitAnswer(QUESTION_ID, otherChoiceId)).willThrow(exception);

        postAnswer(QUESTION_ID, answerBody(otherChoiceId))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("answers-submit-invalid-choice"));
    }

    @Test
    @DisplayName("없는 문제는 404 ProblemDetail로 응답한다")
    void submitAnswerToUnknownQuestion() throws Exception {
        long unknownQuestionId = 999L;
        QuestionNotFoundException exception = new QuestionNotFoundException(unknownQuestionId);
        given(questionService.submitAnswer(unknownQuestionId, CORRECT_CHOICE_ID))
                .willThrow(exception);

        postAnswer(unknownQuestionId, answerBody(CORRECT_CHOICE_ID))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("answers-submit-question-not-found"));
    }

    @Test
    @DisplayName("처리하지 못한 예외는 500 ProblemDetail로 응답하고 내부 메시지를 노출하지 않는다 (D14)")
    void hidesInternalMessageOfUnexpectedException() throws Exception {
        given(questionService.submitAnswer(QUESTION_ID, CORRECT_CHOICE_ID))
                .willThrow(new IllegalStateException(INTERNAL_MESSAGE));

        postAnswer(QUESTION_ID, answerBody(CORRECT_CHOICE_ID))
                .andExpect(problemDetail(HttpStatus.INTERNAL_SERVER_ERROR))
                .andExpect(content().string(not(containsString(INTERNAL_MESSAGE))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("주관식 답을 제출하면 모범 답안과 해설을 응답한다 (R4, D1)")
    void submitTextAnswer() throws Exception {
        given(questionService.submitTextAnswer(SHORT_ANSWER_QUESTION_ID))
                .willReturn(new TextAnswerResponse(MODEL_ANSWER, EXPLANATION));

        postTextAnswer(SHORT_ANSWER_QUESTION_ID, "{\"answer\": \"내가 쓴 답\\n둘째 줄\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("modelAnswer", "explanation")))
                .andExpect(jsonPath("$.modelAnswer").value(MODEL_ANSWER))
                .andExpect(jsonPath("$.explanation").value(EXPLANATION))
                .andDo(document(
                        "text-answers-submit",
                        pathParameters(parameterWithName("questionId").description("문제 ID")),
                        requestFields(fieldWithPath("answer").description("내가 쓴 답 (공백만 있으면 안 되고 5000자 이하)")),
                        responseFields(
                                fieldWithPath("modelAnswer").description("모범 답안"),
                                fieldWithPath("explanation").description("해설 (없으면 null)"))));
    }

    @Test
    @DisplayName("해설이 없는 주관식 문제는 explanation 필드를 null로 응답한다")
    void submitTextAnswerWithoutExplanation() throws Exception {
        given(questionService.submitTextAnswer(SHORT_ANSWER_QUESTION_ID))
                .willReturn(new TextAnswerResponse(MODEL_ANSWER, null));

        postTextAnswer(SHORT_ANSWER_QUESTION_ID, textAnswerBody("answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAnswer").value(MODEL_ANSWER))
                .andExpect(jsonPath("$.explanation").hasJsonPath())
                .andExpect(jsonPath("$.explanation").value(nullValue()));
    }

    @Test
    @DisplayName("답이 정확히 5000자면 받는다 (D2)")
    void acceptsTextAnswerOfMaxLength() throws Exception {
        given(questionService.submitTextAnswer(SHORT_ANSWER_QUESTION_ID))
                .willReturn(new TextAnswerResponse(MODEL_ANSWER, EXPLANATION));

        postTextAnswer(SHORT_ANSWER_QUESTION_ID, textAnswerBody("a".repeat(5000)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("답이 5000자를 넘으면 400 ProblemDetail이고 Service를 호출하지 않는다 (D2)")
    void rejectsTooLongTextAnswer() throws Exception {
        postTextAnswer(SHORT_ANSWER_QUESTION_ID, textAnswerBody("a".repeat(5001)))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(questionService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"answer\": null}",
                "{\"answer\": \"\"}",
                "{\"answer\": \"   \"}",
                "{\"answer\": \"\\n\\t \"}",
                "{\"answer\": ",
                ""
            })
    @DisplayName("answer 누락, 공백, 깨진 JSON, 빈 본문은 400 ProblemDetail이고 Service를 호출하지 않는다 (R8)")
    void rejectsInvalidTextAnswerBody(String body) throws Exception {
        postTextAnswer(SHORT_ANSWER_QUESTION_ID, body).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(questionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공백뿐인 답의 400 응답을 문서화한다")
    void documentsBlankTextAnswer() throws Exception {
        postTextAnswer(SHORT_ANSWER_QUESTION_ID, textAnswerBody("   "))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andDo(document("text-answers-submit-invalid-answer"));
    }

    @Test
    @DisplayName("text-answers 경로의 questionId가 숫자가 아니면 400 ProblemDetail이고 Service를 호출하지 않는다")
    void rejectsNonNumericQuestionIdOfTextAnswer() throws Exception {
        postTextAnswer("abc", textAnswerBody("answer")).andExpect(problemDetail(HttpStatus.BAD_REQUEST));

        then(questionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("객관식 문제에 주관식 답을 제출하면 400 ProblemDetail로 응답한다 (R8, D4)")
    void rejectsTextAnswerToMultipleChoice() throws Exception {
        UnsupportedAnswerTypeException exception = new UnsupportedAnswerTypeException(QuestionType.MULTIPLE_CHOICE);
        given(questionService.submitTextAnswer(QUESTION_ID)).willThrow(exception);

        postTextAnswer(QUESTION_ID, textAnswerBody("answer"))
                .andExpect(problemDetail(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("text-answers-submit-unsupported-type"));
    }

    @Test
    @DisplayName("없는 문제에 주관식 답을 제출하면 404 ProblemDetail로 응답한다 (R8)")
    void submitTextAnswerToUnknownQuestion() throws Exception {
        long unknownQuestionId = 999L;
        QuestionNotFoundException exception = new QuestionNotFoundException(unknownQuestionId);
        given(questionService.submitTextAnswer(unknownQuestionId)).willThrow(exception);

        postTextAnswer(unknownQuestionId, textAnswerBody("answer"))
                .andExpect(problemDetail(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value(exception.getMessage()))
                .andDo(document("text-answers-submit-question-not-found"));
    }

    private ResultActions postTextAnswer(Object questionId, String body) throws Exception {
        return mvc.perform(post("/api/v1/questions/{questionId}/text-answers", questionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String textAnswerBody(String answer) {
        return "{\"answer\": \"" + answer + "\"}";
    }

    private ResultActions postAnswer(Object questionId, String body) throws Exception {
        return mvc.perform(post("/api/v1/questions/{questionId}/answers", questionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String answerBody(long choiceId) {
        return "{\"choiceId\": " + choiceId + "}";
    }

    private static ResultMatcher problemDetail(HttpStatus expected) {
        return result -> {
            status().is(expected.value()).match(result);
            content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(expected.value()).match(result);
            jsonPath("$.title").exists().match(result);
        };
    }
}
