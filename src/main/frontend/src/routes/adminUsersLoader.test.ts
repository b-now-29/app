// 회원 관리 화면의 loader (docs/features/admin-members.md R6, role-owner-category-admin 화면 흐름).
// 구현할 파일: src/routes/adminUsersLoader.ts
//   export async function adminUsersLoader(): Promise<readonly AdminUserResponse[]>      /admin/users
//   export async function adminUserLoader({ params }: LoaderFunctionArgs)
//     : Promise<{ user: AdminUserResponse, categories: readonly AdminCategoryResponse[] }>  /admin/users/:id
// 목록: getAdminUsers로 회원 목록을 받아 돌려준다.
// 상세: getAdminUser(id)와 getAdminCategories() (OWNER라 전체 카테고리)를 함께 받아 { user, categories }로 돌려준다.
// 받은 회원이 OWNER면 /admin/users로 보낸다 (D16).
// 401이면 /login, 403이면 홈(/)으로 보내고 (adminLoader.ts의 rethrowAsRedirect와 같다), 그 밖의 실패는 에러 화면이
// 그리도록 다시 던진다.
import type { LoaderFunctionArgs } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminCategories, getAdminUser, getAdminUsers } from "../api/admin";
import type { AdminCategoryResponse, AdminUserResponse } from "../api/admin";
import { ApiError, NotFoundError } from "../api/http";
import { adminUserLoader, adminUsersLoader } from "./adminUsersLoader";

vi.mock("../api/admin");

const MEMBER: AdminUserResponse = {
  id: 3,
  email: "manager@example.com",
  role: "CATEGORY_ADMIN",
  createdAt: null,
  nickname: "매니저",
  tag: 1,
  categorySlugs: ["java"],
};

const MEMBERS: readonly AdminUserResponse[] = [
  {
    id: 1,
    email: "admin@example.com",
    role: "OWNER",
    createdAt: null,
    nickname: "관리자",
    tag: 1,
    categorySlugs: [],
  },
  MEMBER,
];

const CATEGORIES: readonly AdminCategoryResponse[] = [
  {
    slug: "java",
    name: "Java",
    description: null,
    sortOrder: 10,
    questionCount: 5,
  },
  {
    slug: "spring",
    name: "Spring",
    description: null,
    sortOrder: 20,
    questionCount: 0,
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

function loadUser(id: string) {
  return adminUserLoader({
    request: new Request(`http://localhost/admin/users/${id}`),
    params: { id },
    context: undefined,
  } as unknown as LoaderFunctionArgs);
}

beforeEach(() => {
  vi.mocked(getAdminUsers).mockReset();
  vi.mocked(getAdminUser).mockReset();
  vi.mocked(getAdminCategories).mockReset();
});

describe("adminUsersLoader", () => {
  it("OWNER면 회원 목록을 돌려준다 (R1)", async () => {
    vi.mocked(getAdminUsers).mockResolvedValue(MEMBERS);

    await expect(adminUsersLoader()).resolves.toEqual(MEMBERS);
  });

  it("비회원(401)이면 /login으로 보낸다 (R6)", async () => {
    vi.mocked(getAdminUsers).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectTo(await settle(adminUsersLoader()), "/login");
  });

  it("USER·CATEGORY_ADMIN 회원(403)이면 홈으로 보낸다 (R6, role-owner-category-admin R4)", async () => {
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

describe("adminUserLoader (회원 상세)", () => {
  it("경로의 id로 회원을 받고, 전체 카테고리와 함께 돌려준다", async () => {
    vi.mocked(getAdminUser).mockResolvedValue(MEMBER);
    vi.mocked(getAdminCategories).mockResolvedValue(CATEGORIES);

    await expect(loadUser("3")).resolves.toEqual({
      user: MEMBER,
      categories: CATEGORIES,
    });
    expect(getAdminUser).toHaveBeenCalledWith(3);
  });

  it("받은 회원이 OWNER면 역할을 바꿀 수 없으므로 회원 목록으로 보낸다 (R2, D16)", async () => {
    vi.mocked(getAdminUser).mockResolvedValue(MEMBERS[0]);
    vi.mocked(getAdminCategories).mockResolvedValue(CATEGORIES);

    expectRedirectTo(await settle(loadUser("1")), "/admin/users");
  });

  it("비회원(401)이면 /login으로 보낸다", async () => {
    vi.mocked(getAdminUser).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );
    vi.mocked(getAdminCategories).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectTo(await settle(loadUser("3")), "/login");
  });

  it("OWNER가 아니면(403) 홈으로 보낸다", async () => {
    vi.mocked(getAdminUser).mockRejectedValue(
      new ApiError(403, "권한이 없습니다."),
    );
    vi.mocked(getAdminCategories).mockResolvedValue(CATEGORIES);

    expectRedirectTo(await settle(loadUser("3")), "/");
  });

  it("없는 회원(404)은 다른 화면으로 보내지 않고 에러로 던진다", async () => {
    vi.mocked(getAdminUser).mockRejectedValue(
      new NotFoundError("회원을 찾을 수 없습니다: 999"),
    );
    vi.mocked(getAdminCategories).mockResolvedValue(CATEGORIES);

    await expect(loadUser("999")).rejects.toBeInstanceOf(NotFoundError);
  });
});
