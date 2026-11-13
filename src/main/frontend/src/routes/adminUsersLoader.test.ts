// 회원 관리 화면(/admin/users)의 loader (docs/features/admin-members.md R6, 화면 흐름).
// 구현할 파일: src/routes/adminUsersLoader.ts
//   export async function adminUsersLoader(): Promise<readonly AdminUserResponse[]>
// getAdminUsers로 회원 목록을 받아 돌려준다. 401이면 /login, 403이면 홈(/)으로 보내고 (adminLoader.ts의
// rethrowAsRedirect와 같다), 그 밖의 실패는 에러 화면이 그리도록 다시 던진다.
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminUsers } from "../api/admin";
import type { AdminUserResponse } from "../api/admin";
import { ApiError } from "../api/users";
import { adminUsersLoader } from "./adminUsersLoader";

vi.mock("../api/admin");

const MEMBERS: readonly AdminUserResponse[] = [
  {
    id: 1,
    email: "admin@example.com",
    role: "ADMIN",
    createdAt: null,
    nickname: "관리자",
    tag: 1,
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
  vi.mocked(getAdminUsers).mockReset();
});

describe("adminUsersLoader", () => {
  it("관리자면 회원 목록을 돌려준다 (R1)", async () => {
    vi.mocked(getAdminUsers).mockResolvedValue(MEMBERS);

    await expect(adminUsersLoader()).resolves.toEqual(MEMBERS);
  });

  it("비회원(401)이면 /login으로 보낸다 (R6)", async () => {
    vi.mocked(getAdminUsers).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectTo(await settle(adminUsersLoader()), "/login");
  });

  it("USER 회원(403)이면 홈으로 보낸다 (R6)", async () => {
    vi.mocked(getAdminUsers).mockRejectedValue(
      new ApiError(403, "권한이 없습니다."),
    );

    expectRedirectTo(await settle(adminUsersLoader()), "/");
  });

  it("서버 오류는 다른 화면으로 보내지 않고 에러로 던진다", async () => {
    vi.mocked(getAdminUsers).mockRejectedValue(new ApiError(500, "서버 오류"));

    await expect(adminUsersLoader()).rejects.toBeInstanceOf(ApiError);
  });
});
