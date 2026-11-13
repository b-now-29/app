package com.b.now._29.app.quiz.wronganswer;

import com.b.now._29.app.quiz.question.QuestionResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/wrong-answers")
public class WrongAnswerController {

    private final WrongAnswerService wrongAnswerService;

    public WrongAnswerController(WrongAnswerService wrongAnswerService) {
        this.wrongAnswerService = wrongAnswerService;
    }

    @GetMapping
    public List<WrongAnswerCategoryResponse> getWrongAnswers(@AuthenticationPrincipal Long userId) {
        return wrongAnswerService.getWrongAnswers(userId);
    }

    @GetMapping("/{slug}/questions")
    public List<QuestionResponse> getWrongQuestions(@AuthenticationPrincipal Long userId, @PathVariable String slug) {
        return wrongAnswerService.getWrongQuestions(userId, slug);
    }
}
