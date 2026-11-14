import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategoryStats, getRecentAttempts } from "./attempts";
import type { AttemptResponse, CategoryStatResponse } from "./attempts";
import { ApiError } from "./http";

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

beforeEach(() => {
  fetchMock.mockReset();
});

describe("getCategoryStats", () => {
  const stats: readonly CategoryStatResponse[] = [
    {
      slug: "java",
      name: "자바",
      types: [
        { type: "MULTIPLE_CHOICE", solvedQuestions: 6, correctQuestions: 5 },
        { type: "ESSAY", solvedQuestions: 4, correctQuestions: 2 },
      ],
    },
  ];

  it("내 카테고리별 정답률을 조회해 응답 본문을 그대로 돌려준다 (R6)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(stats));

    await expect(getCategoryStats()).resolves.toEqual(stats);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/category-stats");
  });

  it("기록이 없으면 빈 배열을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(getCategoryStats()).resolves.toEqual([]);
  });

  // loader가 401이면 로그인 화면으로 보내야 하므로 상태 코드를 담아 던진다
  it.each([401, 500])(
    "%i 응답이면 상태 코드를 담은 ApiError를 던진다 (R8)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status));

      const error = await caught(getCategoryStats());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status });
    },
  );
});

describe("getRecentAttempts", () => {
  const attempts: readonly AttemptResponse[] = [
    {
      id: 42,
      questionId: 3,
      questionContent: "문제 본문",
      questionType: "ESSAY",
      categoryName: "자바",
      correct: false,
      attemptedAt: "2026-11-06T01:23:45Z",
    },
  ];

  it("내 최근 풀이 목록을 조회해 응답 본문을 그대로 돌려준다 (R7)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(attempts));

    await expect(getRecentAttempts()).resolves.toEqual(attempts);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/attempts");
  });

  it("기록이 없으면 빈 배열을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(getRecentAttempts()).resolves.toEqual([]);
  });

  it.each([401, 500])(
    "%i 응답이면 상태 코드를 담은 ApiError를 던진다 (R8)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status));

      const error = await caught(getRecentAttempts());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status });
    },
  );
});
