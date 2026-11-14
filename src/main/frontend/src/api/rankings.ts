import { z } from "zod";
import { getJson } from "./http";

const RankingEntrySchema = z
  .object({
    rank: z.number(),
    nickname: z.string(),
    tag: z.number(),
    score: z.number(),
  })
  .readonly();
export type RankingEntryResponse = z.infer<typeof RankingEntrySchema>;

// 로그인한 회원의 순위. 점수가 0이면 rank가 null이다
const MyRankingSchema = z
  .object({
    rank: z.number().nullable(),
    score: z.number(),
  })
  .readonly();
export type MyRankingResponse = z.infer<typeof MyRankingSchema>;

const RankingSchema = z
  .object({
    entries: z.array(RankingEntrySchema).readonly(),
    // 비회원·관리자면 null
    me: MyRankingSchema.nullable(),
  })
  .readonly();
export type RankingResponse = z.infer<typeof RankingSchema>;

export function getRankings(): Promise<RankingResponse> {
  return getJson("/api/v1/rankings", RankingSchema);
}

export function getCategoryRankings(slug: string): Promise<RankingResponse> {
  // slug는 주소창에서 온 값이므로, 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  return getJson(
    `/api/v1/categories/${encodeURIComponent(slug)}/rankings`,
    RankingSchema,
  );
}
