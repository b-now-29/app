// 랭킹 route와 메뉴 (docs/features/ranking.md R10, 화면 흐름).
// appRoutes에 추가할 route: /ranking → RankingPage, loader: rankingLoader, ErrorBoundary: QuizErrorPage (없는 카테고리는 404 화면)
// 메뉴 (RootLayout)에 /ranking으로 가는 "랭킹" 링크를 회원·비회원 모두에게 둔다.
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "./api/categories";
import { NotFoundError } from "./api/questions";
import { getCategoryRankings, getRankings } from "./api/rankings";
import { getCurrentUser } from "./api/users";
import type { UserResponse } from "./api/users";
import { appRoutes } from "./appRoutes";

vi.mock("./api/categories");

vi.mock("./api/rankings", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/rankings")>()),
  getRankings: vi.fn(),
  getCategoryRankings: vi.fn(),
}));

// 모듈 전체를 자동으로 대체하면 ApiError도 가짜가 되어 status와 message가 비게 된다
vi.mock("./api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/users")>()),
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  signUp: vi.fn(),
}));

const USER: UserResponse = {
  id: 1,
  email: "user@example.com",
  role: "USER",
  nickname: "철수",
  tag: 3,
};
const MENU_LINK = "랭킹";
const TITLE = "랭킹";

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getCurrentUser).mockResolvedValue(USER);
  vi.mocked(getCategories).mockResolvedValue([{ slug: "java", name: "자바" }]);
  vi.mocked(getRankings).mockResolvedValue({
    entries: [{ rank: 1, nickname: "철수", tag: 3, score: 42 }],
    me: { rank: 1, score: 42 },
  });
  vi.mocked(getCategoryRankings).mockImplementation((slug) =>
    slug === "java"
      ? Promise.resolve({
          entries: [{ rank: 1, nickname: "자바왕", tag: 1, score: 7 }],
          me: null,
        })
      : Promise.reject(new NotFoundError(`없는 카테고리: ${slug}`)),
  );
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return { router, user: userEvent.setup() };
}

async function findMenu() {
  return within(await screen.findByRole("navigation"));
}

async function expectRankingScreen() {
  expect(
    await screen.findByRole("heading", { name: TITLE, level: 1 }),
  ).toBeInTheDocument();
}

describe("랭킹 메뉴 (R10)", () => {
  it("회원 메뉴에 /ranking으로 가는 랭킹 링크가 있다", async () => {
    renderApp("/");

    const menu = await findMenu();
    expect(await menu.findByRole("link", { name: MENU_LINK })).toHaveAttribute(
      "href",
      "/ranking",
    );
  });

  it("비회원 메뉴에도 랭킹 링크가 있다 (D6)", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(null);
    renderApp("/");

    const menu = await findMenu();
    await menu.findByRole("link", { name: "로그인" });
    expect(menu.getByRole("link", { name: MENU_LINK })).toHaveAttribute(
      "href",
      "/ranking",
    );
  });

  it("메뉴의 랭킹 링크를 누르면 전체 랭킹 화면으로 간다", async () => {
    const { router, user } = renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("link", { name: MENU_LINK }));

    await expectRankingScreen();
    expect(router.state.location.pathname).toBe("/ranking");
    expect(getRankings).toHaveBeenCalled();
    expect(
      within(screen.getByRole("main")).getByText("철수#3"),
    ).toBeInTheDocument();
  });
});

describe("랭킹 route", () => {
  it("비회원도 /ranking에서 랭킹을 본다 (R8)", async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(null);
    vi.mocked(getRankings).mockResolvedValue({
      entries: [{ rank: 1, nickname: "철수", tag: 3, score: 42 }],
      me: null,
    });

    renderApp("/ranking");

    await expectRankingScreen();
    expect(
      within(screen.getByRole("main")).getByText("철수#3"),
    ).toBeInTheDocument();
  });

  it("/ranking?category=java는 그 카테고리 랭킹을 보여 준다 (D8)", async () => {
    renderApp("/ranking?category=java");

    await expectRankingScreen();
    expect(getCategoryRankings).toHaveBeenCalledWith("java");
    expect(
      screen.getByRole("combobox", { name: "카테고리" }),
    ).toHaveDisplayValue("자바");
  });

  it("없거나 삭제된 카테고리면 기존 404 화면을 보여 주고 메뉴는 남는다 (R9)", async () => {
    renderApp("/ranking?category=nope");

    expect(
      await screen.findByRole("heading", { name: "페이지를 찾을 수 없습니다" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("navigation")).toBeInTheDocument();
  });
});
