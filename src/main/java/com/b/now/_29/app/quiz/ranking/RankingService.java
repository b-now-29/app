package com.b.now._29.app.quiz.ranking;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.user.UserRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
// REPEATABLE_READ: 카테고리 확인과 랭킹 쿼리가 한 스냅숏을 본다. READ COMMITTED는 쿼리마다 스냅숏이 달라
// 삭제 직후 404 대신 빈 200이 나올 수 있다 (D14). 목록과 내 순위는 한 쿼리라 항상 같은 스냅숏이다.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RankingService {

    private static final int TOP_LIMIT = 50;

    private final RankingRepository rankingRepository;
    private final CategoryRepository categoryRepository;
    private final UserRoleService userRoleService;

    public RankingService(
            RankingRepository rankingRepository,
            CategoryRepository categoryRepository,
            UserRoleService userRoleService) {
        this.rankingRepository = rankingRepository;
        this.categoryRepository = categoryRepository;
        this.userRoleService = userRoleService;
    }

    /** userId는 비회원이면 null이다. */
    public RankingResponse getRanking(Long userId) {
        RankingViewer viewer = viewer(userId);
        return RankingResponse.of(rankingRepository.findRankings(TOP_LIMIT, viewer.memberId()), viewer);
    }

    public RankingResponse getCategoryRanking(String slug, Long userId) {
        if (!categoryRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new CategoryNotFoundException(slug);
        }
        RankingViewer viewer = viewer(userId);
        return RankingResponse.of(rankingRepository.findRankingsInCategory(slug, TOP_LIMIT, viewer.memberId()), viewer);
    }

    /** 비회원과 관리자는 ANONYMOUS다. memberId가 null이면 쿼리는 상위 목록만 돌려준다 (R3, R8). */
    private RankingViewer viewer(Long userId) {
        return userId == null || userRoleService.isAdmin(userId) ? RankingViewer.ANONYMOUS : new RankingViewer(userId);
    }
}
