package com.b.now._29.app.quiz.ranking;

import com.b.now._29.app.quiz.category.CategoryNotFoundException;
import com.b.now._29.app.quiz.category.CategoryRepository;
import com.b.now._29.app.user.UserRoleService;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
// REPEATABLE_READ: 카테고리 확인·상위 목록·내 순위가 한 스냅숏을 본다. READ COMMITTED는 쿼리마다 스냅숏이 달라
// me.rank와 목록이 어긋나거나, 삭제 직후 404 대신 빈 200이 나올 수 있다 (D14).
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
        return respond(rankingRepository.findTopRankings(TOP_LIMIT), userId, rankingRepository::findUserRanking);
    }

    public RankingResponse getCategoryRanking(String slug, Long userId) {
        if (!categoryRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new CategoryNotFoundException(slug);
        }
        return respond(
                rankingRepository.findTopRankingsInCategory(slug, TOP_LIMIT),
                userId,
                id -> rankingRepository.findUserRankingInCategory(slug, id));
    }

    private RankingResponse respond(List<RankingRow> rows, Long userId, LongFunction<Optional<RankingRow>> findMine) {
        List<RankingEntryResponse> entries =
                rows.stream().map(RankingEntryResponse::from).toList();
        return new RankingResponse(entries, myRanking(userId, findMine));
    }

    /** 비회원과 관리자는 null (R3, R8). 랭킹에 없는 회원은 rank null, score 0 (R7). */
    private MyRankingResponse myRanking(Long userId, LongFunction<Optional<RankingRow>> findMine) {
        if (userId == null || userRoleService.isAdmin(userId)) {
            return null;
        }
        return MyRankingResponse.from(findMine.apply(userId));
    }
}
