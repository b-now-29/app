// 관리자 화면 공통 틀(/admin)의 loader. 구현할 파일: src/routes/adminLoader.ts
//   export async function adminLoader(): Promise<readonly AdminCategoryResponse[]>
// 관리자 카테고리 목록을 받아 돌려준다 (카테고리 탭과 문제 화면의 카테고리 선택에 쓴다).
// 401이면 /login, 403이면 홈(/)으로 보내고, 그 밖의 실패는 에러 화면이 그리도록 다시 던진다.
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminCategories } from "../api/admin";
import type { AdminCategoryResponse } from "../api/admin";
import { ApiError } from "../api/users";
import { adminLoader } from "./adminLoader";

vi.mock("../api/admin");

const CATEGORIES: readonly AdminCategoryResponse[] = [
  {
    slug: "java",
    name: "Java",
    description: null,
    sortOrder: 10,
    questionCount: 5,
  },
];

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
  vi.mocked(getAdminCategories).mockReset();
});

describe("adminLoader", () => {
  it("관리자면 관리자 카테고리 목록을 돌려준다", async () => {
    vi.mocked(getAdminCategories).mockResolvedValue(CATEGORIES);

    await expect(adminLoader()).resolves.toEqual(CATEGORIES);
  });

  it("비회원(401)이면 /login으로 보낸다 (화면 흐름)", async () => {
    vi.mocked(getAdminCategories).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectTo(await settle(adminLoader()), "/login");
  });

  it("USER 회원(403)이면 홈으로 보낸다 (화면 흐름)", async () => {
    vi.mocked(getAdminCategories).mockRejectedValue(
      new ApiError(403, "권한이 없습니다."),
    );

    expectRedirectTo(await settle(adminLoader()), "/");
  });

  it.each([
    ["서버 오류", new ApiError(500, "서버 오류")],
    ["네트워크 오류", new TypeError("Failed to fetch")],
  ])("%s는 다른 화면으로 보내지 않고 에러로 던진다", async (_name, error) => {
    vi.mocked(getAdminCategories).mockRejectedValue(error);

    await expect(adminLoader()).rejects.toBeInstanceOf(Error);
  });
});
