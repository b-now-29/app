package com.b.now._29.app.quiz.attempt;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class QuestionAttemptController {

    private final QuestionAttemptService questionAttemptService;

    public QuestionAttemptController(QuestionAttemptService questionAttemptService) {
        this.questionAttemptService = questionAttemptService;
    }

    @GetMapping("/category-stats")
    public List<CategoryStatResponse> getCategoryStats(@AuthenticationPrincipal Long userId) {
        return questionAttemptService.getCategoryStats(userId);
    }

    @GetMapping("/attempts")
    public List<AttemptResponse> getRecentAttempts(@AuthenticationPrincipal Long userId) {
        return questionAttemptService.getRecentAttempts(userId);
    }
}
