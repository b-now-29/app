package com.b.now._29.app.quiz.admin;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/categories")
    public List<AdminCategoryStatResponse> getCategoryStats() {
        return adminStatsService.getCategoryStats();
    }

    @GetMapping("/categories/{slug}/questions")
    public List<AdminQuestionStatResponse> getQuestionStats(@PathVariable String slug) {
        return adminStatsService.getQuestionStats(slug);
    }
}
