package com.b.now._29.app.quiz.ranking;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 API다. 비회원·잘못된 토큰이면 userId가 null이다 (R8). */
@RestController
@RequestMapping("/api/v1")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/rankings")
    public RankingResponse getRanking(@AuthenticationPrincipal Long userId) {
        return rankingService.getRanking(userId);
    }

    @GetMapping("/categories/{slug}/rankings")
    public RankingResponse getCategoryRanking(@PathVariable String slug, @AuthenticationPrincipal Long userId) {
        return rankingService.getCategoryRanking(slug, userId);
    }
}
