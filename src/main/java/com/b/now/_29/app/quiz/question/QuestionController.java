package com.b.now._29.app.quiz.question;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/categories/{slug}/questions")
    public List<QuestionResponse> getQuestions(@PathVariable String slug) {
        return questionService.getQuestions(slug);
    }

    @PostMapping("/questions/{questionId}/answers")
    public AnswerResponse submitAnswer(
            @PathVariable long questionId,
            @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal Long userId) {
        return questionService.submitAnswer(questionId, request.choiceId(), userId);
    }

    @PostMapping("/questions/{questionId}/self-grades")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void selfGrade(
            @PathVariable long questionId,
            @Valid @RequestBody SelfGradeRequest request,
            @AuthenticationPrincipal Long userId) {
        questionService.selfGrade(questionId, request.correct(), userId);
    }

    @PostMapping("/questions/{questionId}/text-answers")
    public TextAnswerResponse submitTextAnswer(
            @PathVariable long questionId, @Valid @RequestBody TextAnswerRequest request) {
        return questionService.submitTextAnswer(questionId);
    }
}
