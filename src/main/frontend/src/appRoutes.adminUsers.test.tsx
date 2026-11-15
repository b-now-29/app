// 운영자의 회원 관리 화면 (docs/features/admin-members.md, role-owner-category-admin R4, R8, R9, 화면 흐름, D11).
// appRoutes 전체로 그려서 route 구성까지 함께 확인한다.
//
// 구현할 것:
// - src/routes/AdminLayout.tsx: <nav aria-label="관리 메뉴">의 "회원" 링크 (href="/admin/users")는 OWNER에게만
// - src/appRoutes.tsx: admin 자식에 { path: "users/:id", Component: (회원 상세 화면), loader: adminUserLoader }
//   (adminUsersLoader.test.ts 참고)
// - 폼 계산은 src/user/adminUserForm.ts (adminUserForm.test.ts 참고). quiz/adminUsers.ts의 roleAction은 없앤다
//
// 화면 계약 (frontend-dev가 맞출 이름·라벨):
// - 목록 /admin/users: h1 "회원 관리". <ul aria-label="회원 목록">의 항목(listitem)마다 이메일, "닉네임#번호",
//   role 값 (USER·CATEGORY_ADMIN·OWNER 그대로), 담당 카테고리, 링크 "수정 →" (href="/admin/users/{id}").
//   OWNER 행에는 링크가 없다. 서버가 준 순서대로.
// - 상세 /admin/users/:id: h1 "회원 수정". 회원의 이메일과 "닉네임#번호"를 보여 준다.
//   역할 radio "일반 회원" / "카테고리 관리자". 카테고리 관리자를 고르면 카테고리마다 checkbox (라벨 = 카테고리 이름)가
//   보이고, 일반 회원이면 checkbox가 없다. 카테고리 관리자인데 체크가 0개면 "저장" 버튼이 비활성이다.
//   "저장" → changeUserRole(id, role, categorySlugs) → 성공하면 /admin/users로 간다.
//   실패하면 role="alert"에 ApiError의 message (ProblemDetail detail)를 보여 주고 그 화면에 머문다.
//   이 파일의 카테고리는 이름과 slug를 같게 두어, 목록이 담당 카테고리를 slug로 보이든 이름으로 보이든 통과한다.
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  changeUserRole,
  getAdminCategories,
  getAdminUser,
  getAdminUsers,
} from "./api/admin";
import type { AdminCategoryResponse, AdminUserResponse } from "./api/admin";
import { getCategories } from "./api/categories";
import { ApiError, NotFoundError } from "./api/http";
import { getCurrentUser } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/admin");
vi.mock("./api/categories");
vi.mock("./api/users");

const OWNER: UserResponse = {
  id: 1,
  email: "admin@example.com",
  role: "OWNER",
  nickname: "관리자",
  tag: 1,
};
const MANAGER: UserResponse = {
  id: 3,
  email: "manager@example.com",
  role: "CATEGORY_ADMIN",
  nickname: "Kim",
  tag: 12,
};
const USER: UserResponse = {
  id: 2,
  email: "user@example.com",
  role: "USER",
  nickname: "회원",
  tag: 1,
};

const HOME_CATEGORY = "홈 카테고리";

function category(slug: string, sortOrder: number): AdminCategoryResponse {
  return { slug, name: slug, description: null, sortOrder, questionCount: 0 };
}
const ALL_CATEGORIES = [
  category("java", 10),
  category("spring", 20),
  category("kotlin", 30),
];

const MEMBER_OWNER: AdminUserResponse = {
  id: 1,
  email: "admin@example.com",
  role: "OWNER",
  createdAt: null,
  nickname: "관리자",
  tag: 1,
  categorySlugs: [],
};
const MEMBER_USER: AdminUserResponse = {
  id: 2,
  email: "user@example.com",
  role: "USER",
  createdAt: "2026-11-13T12:00:00Z",
  nickname: "회원",
  tag: 1,
  categorySlugs: [],
};
const MEMBER_MANAGER: AdminUserResponse = {
  id: 3,
  email: "manager@example.com",
  role: "CATEGORY_ADMIN",
  createdAt: "2026-03-05T12:00:00Z",
  nickname: "Kim",
  tag: 12,
  categorySlugs: ["java", "spring"],
};

// 서버 상태를 흉내 낸다. 저장 뒤 loader가 다시 실행되어야만 화면에 반영된다
let currentUser: UserResponse | null;
let members: AdminUserResponse[];

function rejectUnlessStaff(): Promise<never> | null {
  if (currentUser === null) {
    return Promise.reject(new ApiError(401, "로그인이 필요합니다."));
  }
  if (currentUser.role === "USER") {
    return Promise.reject(new ApiError(403, "권한이 없습니다."));
  }
  return null;
}

function rejectUnlessOwner(): Promise<never> | null {
  const staff = rejectUnlessStaff();
  if (staff !== null) {
    return staff;
  }
  if (currentUser?.role !== "OWNER") {
    return Promise.reject(new ApiError(403, "권한이 없습니다."));
  }
  return null;
}

beforeEach(() => {
  currentUser = OWNER;
  members = [MEMBER_OWNER, MEMBER_USER, MEMBER_MANAGER];

  vi.mocked(getCurrentUser).mockReset();
  vi.mocked(getCurrentUser).mockImplementation(() =>
    Promise.resolve(currentUser),
  );
  vi.mocked(getCategories).mockReset();
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: HOME_CATEGORY },
  ]);
  vi.mocked(getAdminCategories).mockReset();
  vi.mocked(getAdminCategories).mockImplementation(
    () => rejectUnlessStaff() ?? Promise.resolve([...ALL_CATEGORIES]),
  );

  vi.mocked(getAdminUsers).mockReset();
  vi.mocked(getAdminUsers).mockImplementation(
    () => rejectUnlessOwner() ?? Promise.resolve([...members]),
  );
  vi.mocked(getAdminUser).mockReset();
  vi.mocked(getAdminUser).mockImplementation((id: number) => {
    const denied = rejectUnlessOwner();
    if (denied !== null) {
      return denied;
    }
    const found = members.find((member) => member.id === id);
    return found === undefined
      ? Promise.reject(new NotFoundError(`회원을 찾을 수 없습니다: ${id}`))
      : Promise.resolve(found);
  });
  vi.mocked(changeUserRole).mockReset();
  vi.mocked(changeUserRole).mockImplementation((id, role, categorySlugs) => {
    const found = members.find((member) => member.id === id);
    if (found === undefined) {
      return Promise.reject(new ApiError(404, "회원을 찾을 수 없습니다."));
    }
    const updated = { ...found, role, categorySlugs: [...categorySlugs] };
    members = members.map((member) => (member.id === id ? updated : member));
    return Promise.resolve(updated);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

// 목록(<ul>)은 처음부터 있으므로 findByRole만으로는 다시 받은 목록을 기다리지 않는다.
// waitFor로 조건에 맞는 항목이 나타날 때까지 다시 확인한다.
async function findMemberRow(
  email: string,
  matches: (row: HTMLElement) => boolean = () => true,
) {
  return waitFor(() => {
    const list = screen.getByRole("list", { name: "회원 목록" });
    const row = within(list)
      .getAllByRole("listitem")
      .find((element) => element.textContent?.includes(email));
    if (row === undefined || !matches(row)) {
      throw new Error(`회원 항목이 없거나 조건에 맞지 않습니다: ${email}`);
    }
    return row;
  });
}

async function openDetail(id: number) {
  const router = renderApp(`/admin/users/${id}`);
  await screen.findByRole("heading", { name: "회원 수정", level: 1 });
  return router;
}

const userRadio = () => screen.getByRole("radio", { name: "일반 회원" });
const managerRadio = () =>
  screen.getByRole("radio", { name: "카테고리 관리자" });
const categoryCheckbox = (name: string) =>
  screen.getByRole("checkbox", { name });
const saveButton = () => screen.getByRole("button", { name: "저장" });

describe("관리 탭 (R8)", () => {
  it("OWNER의 관리 메뉴에 /admin/users로 가는 회원 링크가 있고, 누르면 회원 관리 화면이다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/categories");

    const tabs = within(
      await screen.findByRole("navigation", { name: "관리 메뉴" }),
    );
    const link = tabs.getByRole("link", { name: "회원" });
    expect(link).toHaveAttribute("href", "/admin/users");

    await user.click(link);

    expect(
      await screen.findByRole("heading", { name: "회원 관리", level: 1 }),
    ).toBeInTheDocument();
  });
});

describe("회원 목록 (화면 흐름)", () => {
  it("회원마다 이메일, 닉네임#번호, 역할, 담당 카테고리를 서버가 준 순서대로 보여 준다", async () => {
    renderApp("/admin/users");

    const list = await screen.findByRole("list", { name: "회원 목록" });
    const rows = within(list).getAllByRole("listitem");
    expect(rows).toHaveLength(3);

    expect(rows[0]).toHaveTextContent("admin@example.com");
    expect(within(rows[0]).getByText("관리자#1")).toBeInTheDocument();
    expect(rows[0]).toHaveTextContent("OWNER");

    expect(rows[1]).toHaveTextContent("user@example.com");
    expect(within(rows[1]).getByText("회원#1")).toBeInTheDocument();
    expect(rows[1]).toHaveTextContent("USER");

    expect(rows[2]).toHaveTextContent("manager@example.com");
    expect(within(rows[2]).getByText("Kim#12")).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent("CATEGORY_ADMIN");
    expect(rows[2]).toHaveTextContent("java");
    expect(rows[2]).toHaveTextContent("spring");
    expect(rows[2]).not.toHaveTextContent("kotlin");
  });

  it("OWNER가 아닌 회원 행에는 상세로 가는 수정 링크가 있고, OWNER 행에는 없다", async () => {
    renderApp("/admin/users");

    const list = await screen.findByRole("list", { name: "회원 목록" });
    const rows = within(list).getAllByRole("listitem");
    expect(
      within(rows[0]).queryByRole("link", { name: "수정 →" }),
    ).not.toBeInTheDocument();
    expect(
      within(rows[1]).getByRole("link", { name: "수정 →" }),
    ).toHaveAttribute("href", "/admin/users/2");
    expect(
      within(rows[2]).getByRole("link", { name: "수정 →" }),
    ).toHaveAttribute("href", "/admin/users/3");
  });

  it("수정 링크를 누르면 그 회원의 상세 화면이다", async () => {
    const user = userEvent.setup();
    const router = renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("manager@example.com")).getByRole("link", {
        name: "수정 →",
      }),
    );

    expect(
      await screen.findByRole("heading", { name: "회원 수정", level: 1 }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/admin/users/3");
    expect(getAdminUser).toHaveBeenCalledWith(3);
  });
});

describe("회원 상세 (화면 흐름, D11)", () => {
  it("카테고리 관리자는 카테고리 관리자 radio가 골라져 있고, 전체 카테고리 중 담당만 체크되어 있다", async () => {
    await openDetail(3);

    expect(screen.getByText("manager@example.com")).toBeInTheDocument();
    expect(screen.getByText("Kim#12")).toBeInTheDocument();
    expect(managerRadio()).toBeChecked();
    expect(userRadio()).not.toBeChecked();
    expect(categoryCheckbox("java")).toBeChecked();
    expect(categoryCheckbox("spring")).toBeChecked();
    expect(categoryCheckbox("kotlin")).not.toBeChecked();
    expect(saveButton()).toBeEnabled();
  });

  it("일반 회원은 일반 회원 radio가 골라져 있고 카테고리 checkbox가 없다", async () => {
    await openDetail(2);

    expect(userRadio()).toBeChecked();
    expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
    expect(saveButton()).toBeEnabled();
  });

  it("카테고리 관리자를 고르면 카테고리 checkbox가 보이고, 일반 회원으로 돌리면 사라진다", async () => {
    const user = userEvent.setup();
    await openDetail(2);

    await user.click(managerRadio());

    expect(screen.getAllByRole("checkbox")).toHaveLength(3);
    expect(categoryCheckbox("java")).not.toBeChecked();

    await user.click(userRadio());

    expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
  });

  it("카테고리 관리자인데 체크가 0개면 저장 버튼이 꺼지고, 하나를 체크하면 켜진다 (D9)", async () => {
    const user = userEvent.setup();
    await openDetail(2);

    await user.click(managerRadio());
    expect(saveButton()).toBeDisabled();

    await user.click(categoryCheckbox("spring"));
    expect(saveButton()).toBeEnabled();

    await user.click(categoryCheckbox("spring"));
    expect(saveButton()).toBeDisabled();
  });

  it("일반 회원을 카테고리 관리자로 바꿔 저장하면 체크한 카테고리로 요청하고 목록으로 돌아가 바뀐 담당을 보여 준다 (R9)", async () => {
    const user = userEvent.setup();
    const router = await openDetail(2);

    await user.click(managerRadio());
    await user.click(categoryCheckbox("kotlin"));
    await user.click(categoryCheckbox("java"));
    await user.click(saveButton());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/users");
    });
    expect(changeUserRole).toHaveBeenCalledWith(2, "CATEGORY_ADMIN", [
      "kotlin",
      "java",
    ]);
    const row = await findMemberRow(
      "user@example.com",
      (element) => element.textContent?.includes("CATEGORY_ADMIN") === true,
    );
    expect(row).toHaveTextContent("kotlin");
  });

  it("카테고리 관리자의 담당 하나를 해제해 저장하면 남은 담당으로 요청한다", async () => {
    const user = userEvent.setup();
    await openDetail(3);

    await user.click(categoryCheckbox("java"));
    await user.click(saveButton());

    await waitFor(() => {
      expect(changeUserRole).toHaveBeenCalledWith(3, "CATEGORY_ADMIN", [
        "spring",
      ]);
    });
  });

  it("카테고리 관리자를 일반 회원으로 바꿔 저장하면 빈 담당으로 요청한다 (R9)", async () => {
    const user = userEvent.setup();
    const router = await openDetail(3);

    await user.click(userRadio());
    await user.click(saveButton());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/admin/users");
    });
    expect(changeUserRole).toHaveBeenCalledWith(3, "USER", []);
  });

  it.each([
    [409, "운영자의 역할은 바꿀 수 없습니다."],
    [404, "카테고리를 찾을 수 없습니다: java"],
    [400, "입력값이 올바르지 않습니다."],
  ])(
    "저장이 %i로 실패하면 alert에 서버 detail을 보여 주고 상세 화면에 머문다",
    async (status, detail) => {
      vi.mocked(changeUserRole).mockRejectedValue(new ApiError(status, detail));
      const user = userEvent.setup();
      const router = await openDetail(3);

      await user.click(categoryCheckbox("kotlin"));
      await user.click(saveButton());

      expect(await screen.findByRole("alert")).toHaveTextContent(detail);
      expect(router.state.location.pathname).toBe("/admin/users/3");
      expect(categoryCheckbox("kotlin")).toBeChecked();
    },
  );
});

describe("접근 (R4, R8)", () => {
  it("비회원이 회원 목록에 오면 로그인 화면으로 보낸다", async () => {
    currentUser = null;
    const router = renderApp("/admin/users");

    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it.each([
    ["USER", USER],
    ["CATEGORY_ADMIN", MANAGER],
  ])(
    "%s가 회원 목록에 오면 홈으로 보내고 회원 목록을 그리지 않는다",
    async (_name, member) => {
      currentUser = member;
      const router = renderApp("/admin/users");

      expect(
        await screen.findByRole("link", { name: HOME_CATEGORY }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/");
      expect(
        screen.queryByRole("list", { name: "회원 목록" }),
      ).not.toBeInTheDocument();
    },
  );

  it("CATEGORY_ADMIN이 회원 상세에 오면 홈으로 보낸다", async () => {
    currentUser = MANAGER;
    const router = renderApp("/admin/users/2");

    expect(
      await screen.findByRole("link", { name: HOME_CATEGORY }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(
      screen.queryByRole("heading", { name: "회원 수정" }),
    ).not.toBeInTheDocument();
  });
});
