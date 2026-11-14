// 관리자 통계 화면의 loader (docs/features/admin-stats.md R5, 화면 흐름).
// 구현할 파일: src/routes/adminStatsLoaders.ts
//   export async function adminCategoryStatsLoader(): Promise<readonly AdminCategoryStatResponse[]>
//     getAdminCategoryStats()의 결과를 그대로 돌려준다.
//   export async function adminQuestionStatsLoader({ params }: LoaderFunctionArgs): Promise<readonly AdminQuestionStatResponse[]>
//     getAdminQuestionStats(params.slug)의 결과를 그대로 돌려준다. 제목에 쓸 카테고리 이름은 화면이 부모 "admin" loader의
//     카테고리 목록 (useRouteLoaderData("admin"))에서 찾는다 (D8: 부모 loader는 그대로 둔다).
// 둘 다 401이면 /login, 403이면 홈(/)으로 보내고 (adminLoader.ts의 rethrowAsRedirect), 그 밖의 실패 (404 포함)는
// route ErrorBoundary가 그리도록 다시 던진다.
import type { LoaderFunctionArgs } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminCategoryStats, getAdminQuestionStats } from "../api/admin";
import type {
  AdminCategoryStatResponse,
  AdminQuestionStatResponse,
} from "../api/admin";
import { ApiError } from "../api/http";
import {
  adminCategoryStatsLoader,
  adminQuestionStatsLoader,
} from "./adminStatsLoaders";

vi.mock("../api/admin");

const CATEGORY_STATS: readonly AdminCategoryStatResponse[] = [
  {
    slug: "java",
    name: "Java",
    types: [{ type: "MULTIPLE_CHOICE", solved: 12, correct: 9 }],
  },
  { slug: "spring", name: "Spring", types: [] },
];

const QUESTION_STATS: readonly AdminQuestionStatResponse[] = [
  {
    id: 1,
    type: "MULTIPLE_CHOICE",
    content: "문자열 비교는?",
    solvedUsers: 5,
    correctUsers: 4,
  },
];

function questionArgs(slug: string): LoaderFunctionArgs {
  return {
    request: new Request(`http://localhost/admin/stats/${slug}`),
    params: { slug },
    context: undefined,
  } as unknown as LoaderFunctionArgs;
}

// redirect는 돌려줘도 던져도 React Router가 같은 이동으로 처리하므로 둘 다 받는다
async function settle(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

function expectRedirectTo(result: unknown, location: string) {
  expect(result).toBeInstanceOf(Response);
  const response = result as Response;
  expect(response.status).toBeGreaterThanOrEqual(300);
  expect(response.status).toBeLessThan(400);
  expect(response.headers.get("Location")).toBe(location);
}

beforeEach(() => {
  vi.mocked(getAdminCategoryStats).mockReset();
  vi.mocked(getAdminQuestionStats).mockReset();
});

describe("adminCategoryStatsLoader", () => {
  it("관리자면 카테고리 통계를 돌려준다 (R2)", async () => {
    vi.mocked(getAdminCategoryStats).mockResolvedValue(CATEGORY_STATS);

    await expect(adminCategoryStatsLoader()).resolves.toEqual(CATEGORY_STATS);
  });

  it("비회원(401)이면 /login으로 보낸다 (R5)", async () => {
    vi.mocked(getAdminCategoryStats).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectTo(await settle(adminCategoryStatsLoader()), "/login");
  });

  it("USER 회원(403)이면 홈으로 보낸다 (R5)", async () => {
    vi.mocked(getAdminCategoryStats).mockRejectedValue(
      new ApiError(403, "권한이 없습니다."),
    );

    expectRedirectTo(await settle(adminCategoryStatsLoader()), "/");
  });

  it("서버 오류는 다른 화면으로 보내지 않고 에러로 던진다", async () => {
    vi.mocked(getAdminCategoryStats).mockRejectedValue(
      new ApiError(500, "서버 오류"),
    );

    await expect(adminCategoryStatsLoader()).rejects.toBeInstanceOf(ApiError);
  });
});

describe("adminQuestionStatsLoader", () => {
  it("경로의 slug로 문제 통계를 받아 돌려준다 (R3)", async () => {
    vi.mocked(getAdminQuestionStats).mockResolvedValue(QUESTION_STATS);

    await expect(
      adminQuestionStatsLoader(questionArgs("java")),
    ).resolves.toEqual(QUESTION_STATS);
    expect(getAdminQuestionStats).toHaveBeenCalledWith("java");
  });

  it("비회원(401)이면 /login으로 보낸다 (R5)", async () => {
    vi.mocked(getAdminQuestionStats).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectTo(
      await settle(adminQuestionStatsLoader(questionArgs("java"))),
      "/login",
    );
  });

  it("USER 회원(403)이면 홈으로 보낸다 (R5)", async () => {
    vi.mocked(getAdminQuestionStats).mockRejectedValue(
      new ApiError(403, "권한이 없습니다."),
    );

    expectRedirectTo(
      await settle(adminQuestionStatsLoader(questionArgs("java"))),
      "/",
    );
  });

  it("없거나 삭제한 카테고리(404)는 ErrorBoundary가 그리도록 에러로 던진다 (화면 흐름)", async () => {
    vi.mocked(getAdminQuestionStats).mockRejectedValue(
      new ApiError(404, "카테고리를 찾을 수 없습니다."),
    );

    const error = await settle(adminQuestionStatsLoader(questionArgs("nope")));

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404 });
  });
});
