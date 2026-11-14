// 랭킹 화면의 loader (docs/features/ranking.md 화면 흐름, D8).
// 구현할 파일: src/routes/rankingLoader.ts
//   export async function rankingLoader({ request }: LoaderFunctionArgs): Promise<RankingLoaderData>
//   type RankingLoaderData = {
//     category: string;                          // 주소의 ?category= 값. 없거나 빈 값이면 "" (전체)
//     categories: readonly CategoryResponse[];   // 고를 수 있는 카테고리 (getCategories)
//     ranking: RankingResponse;                  // category가 ""이면 getRankings(), 아니면 getCategoryRankings(category)
//   }
// 실패 (404의 NotFoundError 포함)는 그대로 던져 ErrorBoundary (QuizErrorPage)가 그린다. 공개 화면이라 401 처리는 없다.
import type { LoaderFunctionArgs } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "../api/categories";
import type { CategoryResponse } from "../api/categories";
import { ApiError, NotFoundError } from "../api/http";
import { getCategoryRankings, getRankings } from "../api/rankings";
import type { RankingResponse } from "../api/rankings";
import { rankingLoader } from "./rankingLoader";

vi.mock("../api/categories");

vi.mock("../api/rankings", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/rankings")>()),
  getRankings: vi.fn(),
  getCategoryRankings: vi.fn(),
}));

const CATEGORIES: readonly CategoryResponse[] = [
  { slug: "java", name: "자바" },
  { slug: "spring", name: "스프링" },
];

const ALL_RANKING: RankingResponse = {
  entries: [{ rank: 1, nickname: "철수", tag: 3, score: 42 }],
  me: null,
};

const JAVA_RANKING: RankingResponse = {
  entries: [{ rank: 1, nickname: "영희", tag: 1, score: 7 }],
  me: { rank: 1, score: 7 },
};

function loaderArgs(search: string): LoaderFunctionArgs {
  return {
    request: new Request(`http://localhost/ranking${search}`),
    params: {},
    context: undefined,
  } as unknown as LoaderFunctionArgs;
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getCategories).mockResolvedValue(CATEGORIES);
  vi.mocked(getRankings).mockResolvedValue(ALL_RANKING);
  vi.mocked(getCategoryRankings).mockResolvedValue(JAVA_RANKING);
});

describe("rankingLoader", () => {
  it("?category=가 없으면 전체 랭킹을 받고 category는 빈 값이다 (D8)", async () => {
    await expect(rankingLoader(loaderArgs(""))).resolves.toEqual({
      category: "",
      categories: CATEGORIES,
      ranking: ALL_RANKING,
    });
    expect(getRankings).toHaveBeenCalledTimes(1);
    expect(getCategoryRankings).not.toHaveBeenCalled();
  });

  it("?category=가 빈 값이어도 전체 랭킹이다 (D8)", async () => {
    await expect(rankingLoader(loaderArgs("?category="))).resolves.toEqual({
      category: "",
      categories: CATEGORIES,
      ranking: ALL_RANKING,
    });
    expect(getCategoryRankings).not.toHaveBeenCalled();
  });

  it("?category=<slug>가 있으면 그 카테고리 랭킹을 받는다 (R5, D8)", async () => {
    await expect(rankingLoader(loaderArgs("?category=java"))).resolves.toEqual({
      category: "java",
      categories: CATEGORIES,
      ranking: JAVA_RANKING,
    });
    expect(getCategoryRankings).toHaveBeenCalledWith("java");
    expect(getRankings).not.toHaveBeenCalled();
  });

  it("주소에서 인코딩된 slug는 풀어서 API 함수에 넘긴다 (인코딩은 API 함수가 한다)", async () => {
    await rankingLoader(loaderArgs("?category=a%2Fb"));

    expect(getCategoryRankings).toHaveBeenCalledWith("a/b");
  });

  it("없거나 삭제된 카테고리(404)는 ErrorBoundary가 404 화면을 그리도록 NotFoundError를 그대로 던진다 (R9)", async () => {
    const error = new NotFoundError("없는 카테고리: nope");
    vi.mocked(getCategoryRankings).mockRejectedValue(error);

    await expect(rankingLoader(loaderArgs("?category=nope"))).rejects.toBe(
      error,
    );
  });

  it.each([
    ["서버 오류", new ApiError(500, "서버 오류")],
    ["네트워크 오류", new TypeError("Failed to fetch")],
  ])("랭킹 조회의 %s는 그대로 던진다", async (_name, error) => {
    vi.mocked(getRankings).mockRejectedValue(error);

    await expect(rankingLoader(loaderArgs(""))).rejects.toBe(error);
  });

  it("카테고리 목록 조회 실패도 그대로 던진다", async () => {
    const error = new Error("카테고리 목록 조회 실패: HTTP 500");
    vi.mocked(getCategories).mockRejectedValue(error);

    await expect(rankingLoader(loaderArgs(""))).rejects.toBe(error);
  });
});
