import type { LoaderFunctionArgs } from "react-router";
import { getCategories } from "../api/categories";
import type { CategoryResponse } from "../api/categories";
import { getCategoryRankings, getRankings } from "../api/rankings";
import type { RankingResponse } from "../api/rankings";

export type RankingLoaderData = {
  // 주소의 ?category= 값. 없거나 빈 값이면 "" (전체)
  readonly category: string;
  readonly categories: readonly CategoryResponse[];
  readonly ranking: RankingResponse;
};

// 실패 (404의 NotFoundError 포함)는 그대로 던져 ErrorBoundary가 그린다. 공개 화면이라 401 처리는 없다.
export async function rankingLoader({
  request,
}: LoaderFunctionArgs): Promise<RankingLoaderData> {
  const category = new URL(request.url).searchParams.get("category") ?? "";
  const [categories, ranking] = await Promise.all([
    getCategories(),
    category === "" ? getRankings() : getCategoryRankings(category),
  ]);
  return { category, categories, ranking };
}
