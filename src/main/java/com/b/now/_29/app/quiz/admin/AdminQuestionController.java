package com.b.now._29.app.quiz.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/questions")
public class AdminQuestionController {

    private final AdminQuestionService adminQuestionService;

    public AdminQuestionController(AdminQuestionService adminQuestionService) {
        this.adminQuestionService = adminQuestionService;
    }

    @GetMapping
    public List<AdminQuestionResponse> getQuestions(@RequestParam("category") String categorySlug) {
        return adminQuestionService.getQuestions(categorySlug);
    }

    @GetMapping("/{id}")
    public AdminQuestionResponse getQuestion(@PathVariable long id) {
        return adminQuestionService.getQuestion(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminQuestionResponse create(@Valid @RequestBody QuestionRequest request) {
        return adminQuestionService.create(request);
    }

    @PutMapping("/{id}")
    public AdminQuestionResponse update(@PathVariable long id, @Valid @RequestBody QuestionRequest request) {
        return adminQuestionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        adminQuestionService.delete(id);
    }
}
