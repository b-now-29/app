// 랭킹 API 호출 함수 (docs/features/ranking.md API 계약).
// 구현할 파일: src/api/rankings.ts
//   export type RankingEntryResponse = { rank: number; nickname: string; tag: number; score: number }
//   export type MyRankingResponse = { rank: number | null; score: number }
//   export type RankingResponse = { entries: readonly RankingEntryResponse[]; me: MyRankingResponse | null }
//   export function getRankings(): Promise<RankingResponse>                      GET /api/v1/rankings
//   export function getCategoryRankings(slug: string): Promise<RankingResponse>  GET /api/v1/categories/{slug}/rankings
// 실패는 상태 코드를 담은 ApiError로 던진다. 카테고리 랭킹의 404는 기존 문제 API처럼 NotFoundError로 던져
// QuizErrorPage가 404 화면을 그리게 한다 (R9).
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NotFoundError } from "./http";
import { getCategoryRankings, getRankings } from "./rankings";
import type { RankingResponse } from "./rankings";

const fetchMock = vi.spyOn(globalThis, "fetch");

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

function problemResponse(status: number): Response {
  return jsonResponse({ title: "실패", status }, status);
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

const RANKING: RankingResponse = {
  entries: [
    { rank: 1, nickname: "철수", tag: 3, score: 42 },
    { rank: 2, nickname: "영희", tag: 1, score: 40 },
  ],
  me: { rank: 77, score: 5 },
};

beforeEach(() => {
  fetchMock.mockReset();
});

describe("getRankings", () => {
  it("전체 랭킹을 조회해 응답 본문을 그대로 돌려준다 (R5)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(RANKING));

    await expect(getRankings()).resolves.toEqual(RANKING);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/rankings");
  });

  it("비회원 응답 (me null)과 빈 목록도 그대로 돌려준다 (R8)", async () => {
    const empty: RankingResponse = { entries: [], me: null };
    fetchMock.mockResolvedValue(jsonResponse(empty));

    await expect(getRankings()).resolves.toEqual(empty);
  });

  it("500 응답이면 상태 코드를 담은 ApiError를 던진다", async () => {
    fetchMock.mockResolvedValue(problemResponse(500));

    const error = await caught(getRankings());

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 500 });
  });
});

describe("getCategoryRankings", () => {
  it("카테고리 랭킹을 조회해 응답 본문을 그대로 돌려준다 (R5)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(RANKING));

    await expect(getCategoryRankings("java")).resolves.toEqual(RANKING);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/categories/java/rankings");
  });

  // slug는 주소창 (?category=)에서 온 값이므로 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  it("slug를 인코딩해 호출 경로를 바꿀 수 없게 한다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(RANKING));

    await getCategoryRankings("../admin");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/categories/..%2Fadmin/rankings",
    );
  });

  it("없거나 삭제된 카테고리(404)면 NotFoundError를 던진다 (R9)", async () => {
    fetchMock.mockResolvedValue(problemResponse(404));

    await expect(getCategoryRankings("nope")).rejects.toBeInstanceOf(
      NotFoundError,
    );
  });

  it("500 응답이면 상태 코드를 담은 ApiError를 던진다", async () => {
    fetchMock.mockResolvedValue(problemResponse(500));

    const error = await caught(getCategoryRankings("java"));

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 500 });
  });
});
