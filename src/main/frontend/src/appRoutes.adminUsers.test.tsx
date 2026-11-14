// 관리자 회원 관리 화면 (docs/features/admin-members.md R1~R4, R6, R7, 화면 흐름).
// appRoutes 전체로 그려서 route 구성까지 함께 확인한다.
//
// 구현할 것:
// - src/routes/AdminLayout.tsx: <nav aria-label="관리 메뉴">에 "회원" 링크 (href="/admin/users") 추가
// - src/appRoutes.tsx: admin 자식에 { path: "users", Component: AdminUsersPage, loader: adminUsersLoader }
// - src/routes/AdminUsersPage.tsx (default export), src/routes/adminUsersLoader.ts (adminUsersLoader.test.ts 참고)
// - 계산은 src/quiz/adminUsers.ts의 roleAction (adminUsers.test.ts 참고), 가입일은 src/quiz/date.ts의 formatDate
//   (date.test.ts 참고, docs/features/ranking.md D13)
//
// 화면 계약 (frontend-dev가 맞출 이름·라벨):
// - h1 "회원 관리". 목록 <ul aria-label="회원 목록">의 항목(listitem)마다 이메일, role (USER·ADMIN 그대로),
//   가입일 (YYYY.MM.DD, null이면 "-"), 버튼 (USER면 "관리자로 지정", ADMIN이면 "관리자 해제"). 서버가 준 순서대로.
// - (nickname.md R11) 항목마다 이메일 옆에 "닉네임#번호" 텍스트 (그 문자열만 있는 요소)
// - 내 행 (root loader의 /users/me id와 같은 회원)은 버튼이 비활성이고 "나" 표시가 있다 (텍스트 "나"만 있는 요소).
// - 버튼 → window.confirm → true면 changeUserRole(id, 다음 role) → 성공하면 목록 다시 불러오기 (revalidate).
//   false면 요청하지 않는다.
// - 실패하면 목록 위 role="alert"에 ApiError의 message (ProblemDetail detail)를 보여 주고 목록을 다시 불러온다 (D10).
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { changeUserRole, getAdminCategories, getAdminUsers } from "./api/admin";
import type { AdminUserResponse } from "./api/admin";
import { getCategories } from "./api/categories";
import { ApiError } from "./api/http";
import { getCurrentUser } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/admin");
vi.mock("./api/categories");
vi.mock("./api/users");

const ME: UserResponse = {
  id: 1,
  email: "admin@example.com",
  role: "ADMIN",
  nickname: "관리자",
  tag: 1,
};
const USER: UserResponse = {
  id: 2,
  email: "user@example.com",
  role: "USER",
  nickname: "회원",
  tag: 1,
};

const HOME_CATEGORY = "홈 카테고리";

// 시간대가 달라도 날짜가 바뀌지 않도록 UTC 정오로 둔다
const MEMBER_ME: AdminUserResponse = {
  id: 1,
  email: "admin@example.com",
  role: "ADMIN",
  createdAt: null,
  nickname: "관리자",
  tag: 1,
};
const MEMBER_USER: AdminUserResponse = {
  id: 2,
  email: "user@example.com",
  role: "USER",
  createdAt: "2026-11-13T12:00:00Z",
  nickname: "회원",
  tag: 1,
};
const MEMBER_ADMIN: AdminUserResponse = {
  id: 3,
  email: "other-admin@example.com",
  role: "ADMIN",
  createdAt: "2026-03-05T12:00:00Z",
  nickname: "Kim",
  tag: 12,
};

// 서버 상태를 흉내 낸다. role 변경 뒤 loader가 다시 실행되어야만 화면에 반영된다
let currentUser: UserResponse | null;
let members: AdminUserResponse[];

function rejectUnlessAdmin(): Promise<never> | null {
  if (currentUser === null) {
    return Promise.reject(new ApiError(401, "로그인이 필요합니다."));
  }
  if (currentUser.role !== "ADMIN") {
    return Promise.reject(new ApiError(403, "권한이 없습니다."));
  }
  return null;
}

beforeEach(() => {
  currentUser = ME;
  members = [MEMBER_ME, MEMBER_USER, MEMBER_ADMIN];

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
    () => rejectUnlessAdmin() ?? Promise.resolve([]),
  );

  vi.mocked(getAdminUsers).mockReset();
  vi.mocked(getAdminUsers).mockImplementation(
    () => rejectUnlessAdmin() ?? Promise.resolve([...members]),
  );
  vi.mocked(changeUserRole).mockReset();
  vi.mocked(changeUserRole).mockImplementation((id, role) => {
    const found = members.find((member) => member.id === id);
    if (found === undefined) {
      return Promise.reject(new ApiError(404, "회원을 찾을 수 없습니다."));
    }
    const updated = { ...found, role };
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

function confirmDialog(answer: boolean) {
  return vi.spyOn(window, "confirm").mockReturnValue(answer);
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

describe("관리 탭 (화면 흐름)", () => {
  it("관리 메뉴에 /admin/users로 가는 회원 링크가 있고, 누르면 회원 관리 화면이다", async () => {
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

describe("회원 목록 (R1, R2, R7)", () => {
  it("회원마다 이메일, role, 가입일을 서버가 준 순서대로 보여 주고 가입일이 없으면 -다", async () => {
    renderApp("/admin/users");

    const list = await screen.findByRole("list", { name: "회원 목록" });
    const rows = within(list).getAllByRole("listitem");
    expect(rows).toHaveLength(3);

    expect(rows[0]).toHaveTextContent("admin@example.com");
    expect(rows[0]).toHaveTextContent("ADMIN");
    expect(within(rows[0]).getByText("-")).toBeInTheDocument();

    expect(rows[1]).toHaveTextContent("user@example.com");
    expect(rows[1]).toHaveTextContent("USER");
    expect(rows[1]).toHaveTextContent("2026.11.13");

    expect(rows[2]).toHaveTextContent("other-admin@example.com");
    expect(rows[2]).toHaveTextContent("2026.03.05");
  });

  it("회원마다 이메일 옆에 닉네임#번호를 보여 준다 (nickname R11, D9)", async () => {
    renderApp("/admin/users");

    const list = await screen.findByRole("list", { name: "회원 목록" });
    const rows = within(list).getAllByRole("listitem");
    expect(within(rows[0]).getByText("관리자#1")).toBeInTheDocument();
    expect(within(rows[1]).getByText("회원#1")).toBeInTheDocument();
    expect(within(rows[2]).getByText("Kim#12")).toBeInTheDocument();
  });

  it("USER 회원은 관리자로 지정 버튼, ADMIN 회원은 관리자 해제 버튼이다", async () => {
    renderApp("/admin/users");

    const userRow = await findMemberRow("user@example.com");
    expect(
      within(userRow).getByRole("button", { name: "관리자로 지정" }),
    ).toBeEnabled();
    const adminRow = await findMemberRow("other-admin@example.com");
    expect(
      within(adminRow).getByRole("button", { name: "관리자 해제" }),
    ).toBeEnabled();
  });

  it("내 행은 버튼이 비활성이고 나 표시가 있다. 다른 행에는 나 표시가 없다 (R4, R7)", async () => {
    renderApp("/admin/users");

    const list = await screen.findByRole("list", { name: "회원 목록" });
    const rows = within(list).getAllByRole("listitem");
    expect(within(rows[0]).getByRole("button")).toBeDisabled();
    expect(within(rows[0]).getByText("나")).toBeInTheDocument();
    expect(within(rows[1]).queryByText("나")).not.toBeInTheDocument();
    expect(within(rows[2]).queryByText("나")).not.toBeInTheDocument();
  });
});

describe("role 변경 (R3, R4)", () => {
  it("관리자로 지정을 확인하면 ADMIN으로 요청하고 목록을 다시 불러와 바뀐 role을 보여 준다", async () => {
    const confirm = confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("user@example.com")).getByRole("button", {
        name: "관리자로 지정",
      }),
    );

    expect(confirm).toHaveBeenCalled();
    await waitFor(() => {
      expect(changeUserRole).toHaveBeenCalledWith(2, "ADMIN");
    });
    const row = await findMemberRow(
      "user@example.com",
      (element) =>
        within(element).queryByRole("button", { name: "관리자 해제" }) !== null,
    );
    expect(row).toHaveTextContent("ADMIN");
    expect(getAdminUsers).toHaveBeenCalledTimes(2);
  });

  it("관리자 해제를 확인하면 USER로 요청한다", async () => {
    confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("other-admin@example.com")).getByRole(
        "button",
        { name: "관리자 해제" },
      ),
    );

    await waitFor(() => {
      expect(changeUserRole).toHaveBeenCalledWith(3, "USER");
    });
    await findMemberRow(
      "other-admin@example.com",
      (element) =>
        within(element).queryByRole("button", { name: "관리자로 지정" }) !==
        null,
    );
  });

  it("확인을 취소하면 요청을 보내지 않는다", async () => {
    confirmDialog(false);
    const user = userEvent.setup();
    renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("user@example.com")).getByRole("button", {
        name: "관리자로 지정",
      }),
    );

    expect(changeUserRole).not.toHaveBeenCalled();
    expect(
      within(await findMemberRow("user@example.com")).getByRole("button", {
        name: "관리자로 지정",
      }),
    ).toBeInTheDocument();
  });

  it.each([
    [409, "자기 자신의 관리자 권한은 해제할 수 없습니다."],
    [404, "회원을 찾을 수 없습니다: 3"],
  ])(
    "요청이 %i로 실패하면 목록 위 alert에 서버 detail을 보여 주고 서버 값으로 목록을 다시 불러온다",
    async (status, detail) => {
      vi.mocked(changeUserRole).mockRejectedValue(new ApiError(status, detail));
      confirmDialog(true);
      const user = userEvent.setup();
      renderApp("/admin/users");

      await user.click(
        within(await findMemberRow("other-admin@example.com")).getByRole(
          "button",
          { name: "관리자 해제" },
        ),
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(detail);
      const list = screen.getByRole("list", { name: "회원 목록" });
      expect(within(list).getAllByRole("listitem")).toHaveLength(3);
      expect(
        within(await findMemberRow("other-admin@example.com")).getByRole(
          "button",
          { name: "관리자 해제" },
        ),
      ).toBeInTheDocument();
    },
  );
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((settle) => {
    resolve = settle;
  });
  return { promise, resolve };
}

function roleButton(row: HTMLElement) {
  return within(row).getByRole("button");
}

describe("role 변경 진행 중 (코드 리뷰 D9)", () => {
  it("PUT과 목록 다시 불러오기가 끝날 때까지 모든 행의 버튼이 비활성이고, 끝나면 내 행 외에는 다시 활성이다", async () => {
    const put = deferred<AdminUserResponse>();
    vi.mocked(changeUserRole).mockImplementation(() => put.promise);
    confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("user@example.com")).getByRole("button", {
        name: "관리자로 지정",
      }),
    );

    await waitFor(() => {
      expect(changeUserRole).toHaveBeenCalledWith(2, "ADMIN");
    });
    expect(roleButton(await findMemberRow("user@example.com"))).toBeDisabled();
    expect(
      roleButton(await findMemberRow("other-admin@example.com")),
    ).toBeDisabled();

    // PUT이 끝나도 목록 다시 불러오기가 끝나기 전에는 비활성이다
    const reload = deferred<AdminUserResponse[]>();
    vi.mocked(getAdminUsers).mockImplementation(() => reload.promise);
    const updated: AdminUserResponse = { ...MEMBER_USER, role: "ADMIN" };
    members = [MEMBER_ME, updated, MEMBER_ADMIN];
    put.resolve(updated);

    await waitFor(() => {
      expect(getAdminUsers).toHaveBeenCalledTimes(2);
    });
    expect(roleButton(await findMemberRow("user@example.com"))).toBeDisabled();
    expect(
      roleButton(await findMemberRow("other-admin@example.com")),
    ).toBeDisabled();

    reload.resolve([...members]);

    const userRow = await findMemberRow(
      "user@example.com",
      (element) =>
        within(element).queryByRole("button", { name: "관리자 해제" }) !== null,
    );
    await waitFor(() => {
      expect(roleButton(userRow)).toBeEnabled();
    });
    expect(
      roleButton(await findMemberRow("other-admin@example.com")),
    ).toBeEnabled();
    expect(roleButton(await findMemberRow("admin@example.com"))).toBeDisabled();
  });

  it("진행 중에 버튼을 다시 눌러도 확인 창과 요청이 한 번만 나간다", async () => {
    const put = deferred<AdminUserResponse>();
    vi.mocked(changeUserRole).mockImplementation(() => put.promise);
    const confirm = confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/users");

    const button = within(await findMemberRow("user@example.com")).getByRole(
      "button",
      { name: "관리자로 지정" },
    );
    await user.click(button);
    await waitFor(() => {
      expect(changeUserRole).toHaveBeenCalledTimes(1);
    });

    await user.click(button);
    await user.click(
      roleButton(await findMemberRow("other-admin@example.com")),
    );

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(changeUserRole).toHaveBeenCalledTimes(1);

    put.resolve({ ...MEMBER_USER, role: "ADMIN" });
  });
});

describe("role 변경 실패 후 다시 불러오기 (코드 리뷰 D10)", () => {
  it("409로 실패해도 alert에 detail을 보여 주고 목록을 다시 불러와 서버 role로 바꾼다", async () => {
    const detail = "다른 관리자가 이미 바꿨습니다.";
    vi.mocked(changeUserRole).mockImplementation(() => {
      // 다른 관리자가 먼저 해제했다
      members = members.map((member) =>
        member.id === 3 ? { ...member, role: "USER" as const } : member,
      );
      return Promise.reject(new ApiError(409, detail));
    });
    confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("other-admin@example.com")).getByRole(
        "button",
        { name: "관리자 해제" },
      ),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(detail);
    await waitFor(() => {
      expect(getAdminUsers).toHaveBeenCalledTimes(2);
    });
    const row = await findMemberRow(
      "other-admin@example.com",
      (element) =>
        within(element).queryByRole("button", { name: "관리자로 지정" }) !==
        null,
    );
    expect(row).toHaveTextContent("USER");
    expect(screen.getByRole("alert")).toHaveTextContent(detail);
  });

  it("404로 실패하면 alert에 detail을 보여 주고 목록을 다시 불러와 없는 회원을 뺀다", async () => {
    const detail = "회원을 찾을 수 없습니다: 3";
    vi.mocked(changeUserRole).mockImplementation(() => {
      members = members.filter((member) => member.id !== 3);
      return Promise.reject(new ApiError(404, detail));
    });
    confirmDialog(true);
    const user = userEvent.setup();
    renderApp("/admin/users");

    await user.click(
      within(await findMemberRow("other-admin@example.com")).getByRole(
        "button",
        { name: "관리자 해제" },
      ),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(detail);
    await waitFor(() => {
      expect(getAdminUsers).toHaveBeenCalledTimes(2);
    });
    await waitFor(() => {
      const list = screen.getByRole("list", { name: "회원 목록" });
      expect(within(list).getAllByRole("listitem")).toHaveLength(2);
    });
    expect(
      screen.queryByText("other-admin@example.com"),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(detail);
  });
});

describe("접근 (R6)", () => {
  it("비회원이 오면 로그인 화면으로 보낸다", async () => {
    currentUser = null;
    const router = renderApp("/admin/users");

    expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
  });

  it("USER가 오면 홈으로 보내고 회원 목록을 그리지 않는다", async () => {
    currentUser = USER;
    const router = renderApp("/admin/users");

    expect(
      await screen.findByRole("link", { name: HOME_CATEGORY }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(
      screen.queryByRole("list", { name: "회원 목록" }),
    ).not.toBeInTheDocument();
  });
});
