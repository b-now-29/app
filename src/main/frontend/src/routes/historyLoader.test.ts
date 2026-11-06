import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategoryStats, getRecentAttempts } from "../api/attempts";
import type { AttemptResponse, CategoryStatResponse } from "../api/attempts";
import { ApiError } from "../api/users";
import { historyLoader } from "./historyLoader";

vi.mock("../api/attempts", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/attempts")>()),
  getCategoryStats: vi.fn(),
  getRecentAttempts: vi.fn(),
}));

const STATS: readonly CategoryStatResponse[] = [
  {
    slug: "java",
    name: "자바",
    types: [
      { type: "MULTIPLE_CHOICE", solvedQuestions: 6, correctQuestions: 5 },
    ],
  },
];

const ATTEMPTS: readonly AttemptResponse[] = [
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

// redirect는 돌려줘도 던져도 React Router가 같은 이동으로 처리하므로 둘 다 받는다
async function settle(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

function expectRedirectToLogin(result: unknown) {
  expect(result).toBeInstanceOf(Response);
  const response = result as Response;
  expect(response.status).toBeGreaterThanOrEqual(300);
  expect(response.status).toBeLessThan(400);
  expect(response.headers.get("Location")).toBe("/login");
}

beforeEach(() => {
  vi.mocked(getCategoryStats).mockReset();
  vi.mocked(getRecentAttempts).mockReset();
});

describe("historyLoader (R10)", () => {
  it("카테고리별 정답률과 최근 풀이 목록을 함께 돌려준다", async () => {
    vi.mocked(getCategoryStats).mockResolvedValue(STATS);
    vi.mocked(getRecentAttempts).mockResolvedValue(ATTEMPTS);

    await expect(historyLoader()).resolves.toEqual({
      categoryStats: STATS,
      attempts: ATTEMPTS,
    });
  });

  it("기록이 없으면 빈 목록 둘을 돌려준다", async () => {
    vi.mocked(getCategoryStats).mockResolvedValue([]);
    vi.mocked(getRecentAttempts).mockResolvedValue([]);

    await expect(historyLoader()).resolves.toEqual({
      categoryStats: [],
      attempts: [],
    });
  });

  it.each([
    {
      name: "정답률 조회",
      fail: () =>
        vi
          .mocked(getCategoryStats)
          .mockRejectedValue(new ApiError(401, "로그인이 필요합니다.")),
    },
    {
      name: "최근 풀이 조회",
      fail: () =>
        vi
          .mocked(getRecentAttempts)
          .mockRejectedValue(new ApiError(401, "로그인이 필요합니다.")),
    },
  ])("$name가 401이면 /login으로 보낸다 (R8)", async ({ fail }) => {
    vi.mocked(getCategoryStats).mockResolvedValue(STATS);
    vi.mocked(getRecentAttempts).mockResolvedValue(ATTEMPTS);
    fail();

    expectRedirectToLogin(await settle(historyLoader()));
  });

  it.each([
    ["서버 오류", new ApiError(500, "서버 오류")],
    ["네트워크 오류", new TypeError("Failed to fetch")],
  ])("%s는 로그인 화면으로 보내지 않고 에러로 던진다", async (_name, error) => {
    vi.mocked(getCategoryStats).mockRejectedValue(error);
    vi.mocked(getRecentAttempts).mockResolvedValue(ATTEMPTS);

    // 에러 화면(ErrorBoundary)을 그리도록 던져야 한다
    await expect(historyLoader()).rejects.toBeInstanceOf(Error);
  });
});
