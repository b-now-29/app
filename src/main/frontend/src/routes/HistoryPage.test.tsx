import { render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategoryStats, getRecentAttempts } from "../api/attempts";
import type { AttemptResponse, CategoryStatResponse } from "../api/attempts";
import { ApiError } from "../api/users";
import ErrorPage from "./ErrorPage";
import HistoryPage from "./HistoryPage";
import { historyLoader } from "./historyLoader";

vi.mock("../api/attempts", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/attempts")>()),
  getCategoryStats: vi.fn(),
  getRecentAttempts: vi.fn(),
}));

const LOGIN_SCREEN = "로그인 화면";

const STATS: readonly CategoryStatResponse[] = [
  {
    slug: "java",
    name: "자바",
    types: [
      { type: "MULTIPLE_CHOICE", solvedQuestions: 6, correctQuestions: 5 },
      { type: "ESSAY", solvedQuestions: 4, correctQuestions: 2 },
    ],
  },
  {
    slug: "spring",
    name: "스프링",
    types: [{ type: "SHORT_ANSWER", solvedQuestions: 3, correctQuestions: 1 }],
  },
];

function attempt(
  id: number,
  overrides: Partial<AttemptResponse> = {},
): AttemptResponse {
  return {
    id,
    questionId: id + 100,
    questionContent: `문제 ${id}`,
    questionType: "MULTIPLE_CHOICE",
    categoryName: "자바",
    correct: true,
    attemptedAt: "2026-11-06T01:23:45Z",
    ...overrides,
  };
}

// 같은 문제를 다시 푼 기록(42, 40)도 각각 한 줄이다 (R7)
const ATTEMPTS: readonly AttemptResponse[] = [
  attempt(42, {
    questionId: 3,
    questionContent: "서술형 문제 본문",
    questionType: "ESSAY",
    correct: false,
  }),
  attempt(41, {
    questionId: 7,
    questionContent: "스프링 주관식 본문",
    questionType: "SHORT_ANSWER",
    categoryName: "스프링",
    correct: true,
    attemptedAt: "2026-11-05T09:00:00Z",
  }),
  attempt(40, {
    questionId: 3,
    questionContent: "서술형 문제 본문",
    questionType: "ESSAY",
    correct: true,
    attemptedAt: "2026-11-04T09:00:00Z",
  }),
];

function LoginStub() {
  return <h1>{LOGIN_SCREEN}</h1>;
}

function renderHistory() {
  const router = createMemoryRouter(
    [
      {
        path: "/history",
        Component: HistoryPage,
        loader: historyLoader,
        ErrorBoundary: ErrorPage,
      },
      { path: "/login", Component: LoginStub },
    ],
    { initialEntries: ["/history"] },
  );
  render(<RouterProvider router={router} />);
  return { router };
}

async function startHistory() {
  const rendered = renderHistory();
  await screen.findByRole("heading", { name: "내 기록", level: 1 });
  return rendered;
}

const statsList = () => screen.getByRole("list", { name: "정답률" });
const recentList = () => screen.getByRole("list", { name: "최근 풀이" });
const recentItems = () => within(recentList()).getAllByRole("listitem");

const isBefore = (first: Element, second: Element) =>
  (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !==
  0;

beforeEach(() => {
  vi.mocked(getCategoryStats).mockReset();
  vi.mocked(getRecentAttempts).mockReset();
  vi.mocked(getCategoryStats).mockResolvedValue(STATS);
  vi.mocked(getRecentAttempts).mockResolvedValue(ATTEMPTS);
});

describe("내 기록 화면 (R10)", () => {
  describe("카테고리별 정답률 (R6, D11)", () => {
    it("카테고리마다 유형별 정답률 줄을 서버가 준 순서대로 보여 준다", async () => {
      await startHistory();
      const list = within(statsList());

      const java = list.getByText("자바");
      const javaChoice = list.getByText("객관식 6문제 중 정답 5문제 (83%)");
      const javaEssay = list.getByText("서술형 4문제 중 정답 2문제 (50%)");
      const spring = list.getByText("스프링");
      const springShort = list.getByText("주관식 3문제 중 정답 1문제 (33%)");

      expect(isBefore(java, javaChoice)).toBe(true);
      expect(isBefore(javaChoice, javaEssay)).toBe(true);
      expect(isBefore(javaEssay, spring)).toBe(true);
      expect(isBefore(spring, springShort)).toBe(true);
    });

    it("기록이 없는 유형의 줄은 만들지 않고 유형을 합친 카테고리 합계도 내지 않는다", async () => {
      await startHistory();
      const list = within(statsList());

      expect(list.queryByText(/^주관식/)).toBeInTheDocument();
      expect(list.getAllByText(/문제 중 정답/)).toHaveLength(3);
      // 자바 합계(10문제 중 7문제)를 따로 보여 주지 않는다
      expect(list.queryByText(/10문제/)).not.toBeInTheDocument();
      expect(list.queryByText(/70%/)).not.toBeInTheDocument();
    });
  });

  describe("최근 풀이 목록 (R7, D10)", () => {
    it("정답률 목록 아래에 최근 풀이 목록이 있다", async () => {
      await startHistory();

      expect(isBefore(statsList(), recentList())).toBe(true);
    });

    it("서버가 준 순서대로 한 줄씩 문제 내용과 카테고리, 맞음 여부를 보여 준다", async () => {
      await startHistory();

      const items = recentItems();
      expect(items).toHaveLength(3);

      expect(items[0]).toHaveTextContent("서술형 문제 본문");
      expect(items[0]).toHaveTextContent("자바");
      expect(items[0]).toHaveTextContent("틀림");
      expect(items[0]).not.toHaveTextContent("맞음");

      expect(items[1]).toHaveTextContent("스프링 주관식 본문");
      expect(items[1]).toHaveTextContent("스프링");
      expect(items[1]).toHaveTextContent("맞음");
      expect(items[1]).not.toHaveTextContent("틀림");

      // 같은 문제를 다시 푼 기록도 따로 한 줄이다
      expect(items[2]).toHaveTextContent("서술형 문제 본문");
      expect(items[2]).toHaveTextContent("맞음");
    });

    it("서버가 준 20건을 모두 보여 주고 페이지 이동은 없다", async () => {
      vi.mocked(getRecentAttempts).mockResolvedValue(
        Array.from({ length: 20 }, (_, index) => attempt(100 - index)),
      );
      await startHistory();

      expect(recentItems()).toHaveLength(20);
      expect(recentItems()[0]).toHaveTextContent("문제 100");
      expect(recentItems()[19]).toHaveTextContent("문제 81");
      expect(
        screen.queryByRole("button", { name: /더 보기|다음|이전/ }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("link", { name: /더 보기|다음|이전/ }),
      ).not.toBeInTheDocument();
    });
  });

  describe("기록이 없을 때", () => {
    it("안내 문구를 보여 주고 빈 목록을 그리지 않는다", async () => {
      vi.mocked(getCategoryStats).mockResolvedValue([]);
      vi.mocked(getRecentAttempts).mockResolvedValue([]);

      await startHistory();

      expect(screen.getByText(/풀이 기록이 없/)).toBeInTheDocument();
      expect(
        screen.queryByRole("list", { name: "정답률" }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("list", { name: "최근 풀이" }),
      ).not.toBeInTheDocument();
    });
  });

  describe("불러오기 실패", () => {
    it("비회원(401)이면 로그인 화면으로 보낸다 (R8)", async () => {
      vi.mocked(getCategoryStats).mockRejectedValue(
        new ApiError(401, "로그인이 필요합니다."),
      );
      vi.mocked(getRecentAttempts).mockRejectedValue(
        new ApiError(401, "로그인이 필요합니다."),
      );

      const { router } = renderHistory();

      expect(
        await screen.findByRole("heading", { name: LOGIN_SCREEN }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
    });

    it("그 밖의 실패는 에러 화면을 보여 준다", async () => {
      vi.mocked(getCategoryStats).mockRejectedValue(
        new ApiError(500, "서버 오류"),
      );

      renderHistory();

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "문제가 발생했습니다",
      );
      expect(
        screen.queryByRole("heading", { name: LOGIN_SCREEN }),
      ).not.toBeInTheDocument();
    });
  });
});
