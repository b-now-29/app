import { NotFoundError } from "./questions";
import { toApiError } from "./users";

export type RankingEntryResponse = {
  readonly rank: number;
  readonly nickname: string;
  readonly tag: number;
  readonly score: number;
};

// 로그인한 회원의 순위. 점수가 0이면 rank가 null이다
export type MyRankingResponse = {
  readonly rank: number | null;
  readonly score: number;
};

export type RankingResponse = {
  readonly entries: readonly RankingEntryResponse[];
  // 비회원·관리자면 null
  readonly me: MyRankingResponse | null;
};

export async function getRankings(): Promise<RankingResponse> {
  const response = await fetch("/api/v1/rankings");
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as RankingResponse;
}

export async function getCategoryRankings(
  slug: string,
): Promise<RankingResponse> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  const response = await fetch(
    `/api/v1/categories/${encodeURIComponent(slug)}/rankings`,
  );
  if (response.status === 404) {
    throw new NotFoundError(`없는 카테고리: ${slug}`);
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as RankingResponse;
}
