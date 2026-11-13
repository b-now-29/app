// 관리자 통계 화면 (docs/features/admin-stats.md R2, R3, R5, 화면 흐름, D7, D8).
// appRoutes 전체로 그려서 route 구성까지 함께 확인한다.
//
// 구현할 것:
// - src/routes/AdminLayout.tsx: <nav aria-label="관리 메뉴">에 "통계" 링크 (href="/admin/stats") 추가
// - src/appRoutes.tsx: admin 자식에
//     { path: "stats", Component: AdminCategoryStatsPage, loader: adminCategoryStatsLoader }
//     { path: "stats/:slug", Component: AdminQuestionStatsPage, loader: adminQuestionStatsLoader }
//   404는 route ErrorBoundary (기존 ErrorPage, role="alert")가 그린다.
// - src/routes/AdminCategoryStatsPage.tsx, src/routes/AdminQuestionStatsPage.tsx (default export)
// - src/routes/adminStatsLoaders.ts (adminStatsLoaders.test.ts 참고)
// - 문구는 src/quiz/adminStats.ts의 categoryTypeStatLine·questionStatLine (adminStats.test.ts 참고)
//
// 화면 계약 (frontend-dev가 맞출 이름·라벨):
// - /admin/stats: h1 "통계". 목록 <ul aria-label="카테고리 통계">의 항목(listitem)마다 카테고리 이름 링크
//   (href="/admin/stats/{slug}")와 유형별 한 줄 (categoryTypeStatLine). types가 비면 "아직 풀이 기록이 없습니다.".
//   서버가 준 순서대로.
// - /admin/stats/:slug: h1은 카테고리 이름 (부모 "admin" loader의 카테고리 목록에서 slug로 찾는다).
//   목록 <ul aria-label="문제 통계">의 항목마다 유형 이름 (객관식·주관식·서술형), 문제 내용, questionStatLine
//   ("5명 중 4명 정답 (80%)", 0명이면 "풀이 기록 없음"). 서버가 준 순서대로.
//   통계 목록으로 돌아가는 링크 "통계 목록" (href="/admin/stats").
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getAdminCategories,
  getAdminCategoryStats,
  getAdminQuestionStats,
} from "./api/admin";
import type {
  AdminCategoryResponse,
  AdminCategoryStatResponse,
  AdminQuestionStatResponse,
} from "./api/admin";
import { getCategories } from "./api/categories";
import { ApiError, getCurrentUser } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/admin");
vi.mock("./api/categories");

// 모듈 전체를 자동으로 대체하면 ApiError도 가짜가 되어 status와 message가 비게 된다
vi.mock("./api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/users")>()),
  getCurrentUser: vi.fn(),
}));

const ADMIN: UserResponse = {
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

const JAVA: AdminCategoryResponse = {
  slug: "java",
  name: "Java",
  description: "자바 기초",
  sortOrder: 10,
  questionCount: 3,
};
const SPRING: AdminCategoryResponse = {
  slug: "spring",
  name: "Spring",
  description: null,
  sortOrder: 20,
  questionCount: 0,
};

const CATEGORY_STATS: readonly AdminCategoryStatResponse[] = [
  {
    slug: "java",
    name: "Java",
    types: [
      { type: "MULTIPLE_CHOICE", solved: 12, correct: 9 },
      { type: "ESSAY", solved: 3, correct: 1 },
    ],
  },
  { slug: "spring", name: "Spring", types: [] },
];

const QUESTION_STATS: readonly AdminQuestionStatResponse[] = [
  {
    id: 1,
    type: "MULTIPLE_CHOICE",
    content: "문자열 내용을 비교할 때 쓰는 것은?",
    solvedUsers: 5,
    correctUsers: 4,
  },
  {
    id: 2,
    type: "SHORT_ANSWER",
    content: "JVM의 약자를 쓰세요.",
    solvedUsers: 3,
    correctUsers: 2,
  },
  {
    id: 3,
    type: "ESSAY",
    content: "equals()와 ==를 비교해 서술하세요.",
    solvedUsers: 0,
    correctUsers: 0,
  },
];

let currentUser: UserResponse | null;

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
  currentUser = ADMIN;

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
    () => rejectUnlessAdmin() ?? Promise.resolve([JAVA, SPRING]),
  );

  vi.mocked(getAdminCategoryStats).mockReset();
  vi.mocked(getAdminCategoryStats).mockImplementation(
    () => rejectUnlessAdmin() ?? Promise.resolve([...CATEGORY_STATS]),
  );
  vi.mocked(getAdminQuestionStats).mockReset();
  vi.mocked(getAdminQuestionStats).mockImplementation(
    (slug: string) =>
      rejectUnlessAdmin() ??
      (slug === "java"
        ? Promise.resolve([...QUESTION_STATS])
        : slug === "spring"
          ? Promise.resolve([])
          : Promise.reject(new ApiError(404, "카테고리를 찾을 수 없습니다."))),
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe("관리 탭 (화면 흐름)", () => {
  it("관리 메뉴에 /admin/stats로 가는 통계 링크가 있고, 누르면 카테고리 통계 화면이다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/categories");

    const tabs = within(
      await screen.findByRole("navigation", { name: "관리 메뉴" }),
    );
    const link = tabs.getByRole("link", { name: "통계" });
    expect(link).toHaveAttribute("href", "/admin/stats");

    await user.click(link);

    expect(
      await screen.findByRole("heading", { name: "통계", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("list", { name: "카테고리 통계" }),
    ).toBeInTheDocument();
  });
});

describe("카테고리 통계 /admin/stats (R2)", () => {
  it("카테고리를 서버가 준 순서대로 보여 주고, 유형마다 풀이 수·정답 수·정답률을 한 줄씩 보여 준다", async () => {
    renderApp("/admin/stats");

    const list = await screen.findByRole("list", { name: "카테고리 통계" });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);

    expect(within(items[0]).getByRole("link", { name: "Java" })).toBeVisible();
    expect(
      within(items[0]).getByText("객관식 풀이 12건 중 정답 9건 (75%)"),
    ).toBeInTheDocument();
    expect(
      within(items[0]).getByText("서술형 풀이 3건 중 정답 1건 (33%)"),
    ).toBeInTheDocument();
    expect(
      within(items[0]).queryByText("아직 풀이 기록이 없습니다."),
    ).not.toBeInTheDocument();

    expect(
      within(items[1]).getByRole("link", { name: "Spring" }),
    ).toBeVisible();
  });

  it("types가 비면 아직 풀이 기록이 없습니다를 보여 준다", async () => {
    renderApp("/admin/stats");

    const list = await screen.findByRole("list", { name: "카테고리 통계" });
    const spring = within(list).getAllByRole("listitem")[1];
    expect(
      within(spring).getByText("아직 풀이 기록이 없습니다."),
    ).toBeInTheDocument();
    expect(spring).not.toHaveTextContent("풀이 0건");
  });

  it("카테고리 이름은 /admin/stats/{slug} 링크이고, 누르면 그 카테고리의 문제 통계 화면이다 (D7)", async () => {
    const user = userEvent.setup();
    renderApp("/admin/stats");

    const list = await screen.findByRole("list", { name: "카테고리 통계" });
    const link = within(list).getByRole("link", { name: "Java" });
    expect(link).toHaveAttribute("href", "/admin/stats/java");

    await user.click(link);

    expect(
      await screen.findByRole("heading", { name: "Java", level: 1 }),
    ).toBeInTheDocument();
    expect(getAdminQuestionStats).toHaveBeenCalledWith("java");
  });
});

describe("문제 통계 /admin/stats/:slug (R3)", () => {
  it("제목은 카테고리 이름이고, 문제마다 유형·내용·정답 회원 수를 서버가 준 순서대로 보여 준다", async () => {
    renderApp("/admin/stats/java");

    expect(
      await screen.findByRole("heading", { name: "Java", level: 1 }),
    ).toBeInTheDocument();
    const list = screen.getByRole("list", { name: "문제 통계" });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(3);

    expect(items[0]).toHaveTextContent("객관식");
    expect(items[0]).toHaveTextContent("문자열 내용을 비교할 때 쓰는 것은?");
    expect(items[0]).toHaveTextContent("5명 중 4명 정답 (80%)");

    expect(items[1]).toHaveTextContent("주관식");
    expect(items[1]).toHaveTextContent("JVM의 약자를 쓰세요.");
    expect(items[1]).toHaveTextContent("3명 중 2명 정답 (67%)");

    expect(items[2]).toHaveTextContent("서술형");
    expect(items[2]).toHaveTextContent("equals()와 ==를 비교해 서술하세요.");
  });

  it("푼 회원이 0명인 문제는 풀이 기록 없음이다", async () => {
    renderApp("/admin/stats/java");

    const list = await screen.findByRole("list", { name: "문제 통계" });
    const essay = within(list).getAllByRole("listitem")[2];
    expect(within(essay).getByText("풀이 기록 없음")).toBeInTheDocument();
    expect(essay).not.toHaveTextContent("0명 중");
  });

  it("통계 목록 링크를 누르면 카테고리 통계 화면으로 돌아간다", async () => {
    const user = userEvent.setup();
    renderApp("/admin/stats/java");

    const back = await screen.findByRole("link", { name: "통계 목록" });
    expect(back).toHaveAttribute("href", "/admin/stats");

    await user.click(back);

    expect(
      await screen.findByRole("list", { name: "카테고리 통계" }),
    ).toBeInTheDocument();
  });

  it("없거나 삭제한 카테고리(404)면 에러 화면을 그리고 문제 통계는 없다", async () => {
    renderApp("/admin/stats/nope");

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(
      screen.queryByRole("list", { name: "문제 통계" }),
    ).not.toBeInTheDocument();
  });
});

describe("접근 (R5)", () => {
  it.each(["/admin/stats", "/admin/stats/java"])(
    "비회원이 %s에 오면 로그인 화면으로 보낸다",
    async (path) => {
      currentUser = null;
      const router = renderApp(path);

      expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
    },
  );

  it.each(["/admin/stats", "/admin/stats/java"])(
    "USER가 %s에 오면 홈으로 보내고 통계를 그리지 않는다",
    async (path) => {
      currentUser = USER;
      const router = renderApp(path);

      expect(
        await screen.findByRole("link", { name: HOME_CATEGORY }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/");
      expect(
        screen.queryByRole("list", { name: "카테고리 통계" }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("list", { name: "문제 통계" }),
      ).not.toBeInTheDocument();
    },
  );
});
