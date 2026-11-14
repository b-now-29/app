// 랭킹 화면 (docs/features/ranking.md R6, R7, R10, D8, D9, 화면 흐름).
// 구현할 파일: src/routes/RankingPage.tsx (default export), loader는 rankingLoader.ts의 rankingLoader
// - 제목 (h1) "랭킹"
// - 위쪽에 "카테고리" 라벨의 선택 칸 (components/SelectField 재사용 가능). 첫 항목 "전체" (값 ""), 그 뒤 카테고리 이름.
//   고르면 주소를 /ranking?category=<slug>로 바꾸고 ("전체"면 /ranking) loader가 다시 실행된다 (D8).
// - 회원 (me가 있음)이면 표 위에 "내 순위: N위 (M문제)" 또는 "아직 순위가 없어요". 비회원·관리자 (me null)면 둘 다 없다 (R7).
// - 표 (<table>): 열은 등수, 닉네임 (displayName의 "닉네임#번호"), 점수. 서버가 준 순서대로 한 줄씩 (R6).
// - 자기 줄 강조 (D9): me.rank와 같은 등수의 줄 (<tr>) 하나에 aria-current="true"를 단다. CSS는 검사하지 않는다.
// - 목록이 비면 표 대신 "아직 랭킹이 없어요" 안내 (R10).
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "../api/categories";
import { NotFoundError } from "../api/http";
import { getCategoryRankings, getRankings } from "../api/rankings";
import type { RankingResponse } from "../api/rankings";
import QuizErrorPage from "./QuizErrorPage";
import RankingPage from "./RankingPage";
import { rankingLoader } from "./rankingLoader";

vi.mock("../api/categories");

vi.mock("../api/rankings", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/rankings")>()),
  getRankings: vi.fn(),
  getCategoryRankings: vi.fn(),
}));

const TITLE = "랭킹";
const SELECT_LABEL = "카테고리";
const EMPTY_MESSAGE = /아직 랭킹이 없/;
const NO_RANK = "아직 순위가 없어요";

const ENTRIES = [
  { rank: 1, nickname: "철수", tag: 3, score: 42 },
  { rank: 2, nickname: "영희", tag: 1, score: 40 },
  { rank: 3, nickname: "Kim Lee", tag: 12, score: 38 },
] as const;

const GUEST_RANKING: RankingResponse = { entries: ENTRIES, me: null };

const JAVA_RANKING: RankingResponse = {
  entries: [{ rank: 1, nickname: "자바왕", tag: 1, score: 7 }],
  me: null,
};

function renderRanking(path = "/ranking") {
  const router = createMemoryRouter(
    [
      {
        path: "/ranking",
        Component: RankingPage,
        loader: rankingLoader,
        ErrorBoundary: QuizErrorPage,
      },
    ],
    { initialEntries: [path] },
  );
  render(<RouterProvider router={router} />);
  return { router, user: userEvent.setup() };
}

async function startRanking(path = "/ranking") {
  const rendered = renderRanking(path);
  await screen.findByRole("heading", { name: TITLE, level: 1 });
  return rendered;
}

const categorySelect = () =>
  screen.getByRole("combobox", { name: SELECT_LABEL });

// 머리글 줄을 빼고 칸 (cell)이 있는 줄만
const dataRows = () =>
  within(screen.getByRole("table"))
    .getAllByRole("row")
    .filter((row) => within(row).queryAllByRole("cell").length > 0);

const highlightedRows = () =>
  dataRows().filter((row) => row.getAttribute("aria-current") === "true");

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: "자바" },
    { slug: "spring", name: "스프링" },
  ]);
  vi.mocked(getRankings).mockResolvedValue(GUEST_RANKING);
  vi.mocked(getCategoryRankings).mockResolvedValue(JAVA_RANKING);
});

describe("랭킹 화면", () => {
  describe("표 (R6)", () => {
    it("등수, 닉네임#번호, 점수 열이 있다", async () => {
      await startRanking();

      const headers = screen.getAllByRole("columnheader");
      expect(headers).toHaveLength(3);
      expect(headers[0]).toHaveTextContent("등수");
      expect(headers[1]).toHaveTextContent("닉네임");
      expect(headers[2]).toHaveTextContent("점수");
    });

    it("서버가 준 순서대로 한 줄씩 등수, 닉네임#번호, 점수를 보여 준다", async () => {
      await startRanking();

      const rows = dataRows();
      expect(rows).toHaveLength(3);
      ENTRIES.forEach((entry, index) => {
        const cells = within(rows[index]).getAllByRole("cell");
        expect(cells).toHaveLength(3);
        expect(cells[0]).toHaveTextContent(String(entry.rank));
        expect(cells[1]).toHaveTextContent(`${entry.nickname}#${entry.tag}`);
        expect(cells[2]).toHaveTextContent(String(entry.score));
      });
    });
  });

  describe("카테고리 선택 (R10, D8)", () => {
    it("선택 칸은 전체 다음에 카테고리 이름을 서버 순서대로 둔다", async () => {
      await startRanking();

      const options = within(categorySelect()).getAllByRole("option");
      expect(options.map((option) => option.textContent)).toEqual([
        "전체",
        "자바",
        "스프링",
      ]);
    });

    it("주소에 category가 없으면 처음 값은 전체이고 전체 랭킹을 보여 준다", async () => {
      await startRanking("/ranking");

      expect(categorySelect()).toHaveDisplayValue("전체");
      expect(getRankings).toHaveBeenCalled();
      expect(getCategoryRankings).not.toHaveBeenCalled();
      expect(screen.getByText("철수#3")).toBeInTheDocument();
    });

    it("주소의 category로 처음 값을 정하고 그 카테고리 랭킹을 보여 준다 (새로고침·공유)", async () => {
      await startRanking("/ranking?category=spring");

      expect(categorySelect()).toHaveDisplayValue("스프링");
      expect(getCategoryRankings).toHaveBeenCalledWith("spring");
      expect(screen.getByText("자바왕#1")).toBeInTheDocument();
    });

    it("카테고리를 고르면 주소에 남기고 그 카테고리 랭킹으로 바꾼다", async () => {
      const { router, user } = await startRanking();

      await user.selectOptions(categorySelect(), "자바");

      expect(await screen.findByText("자바왕#1")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/ranking");
      expect(router.state.location.search).toBe("?category=java");
      expect(getCategoryRankings).toHaveBeenCalledWith("java");
      expect(categorySelect()).toHaveDisplayValue("자바");
      expect(screen.queryByText("철수#3")).not.toBeInTheDocument();
    });

    it("전체를 다시 고르면 주소에서 category를 지우고 전체 랭킹으로 돌아간다", async () => {
      const { router, user } = await startRanking("/ranking?category=java");

      await user.selectOptions(categorySelect(), "전체");

      expect(await screen.findByText("철수#3")).toBeInTheDocument();
      expect(router.state.location.search).toBe("");
      expect(categorySelect()).toHaveDisplayValue("전체");
    });

    it("없거나 삭제된 카테고리면 404 화면을 보여 준다 (R9)", async () => {
      vi.mocked(getCategoryRankings).mockRejectedValue(
        new NotFoundError("없는 카테고리: nope"),
      );

      renderRanking("/ranking?category=nope");

      expect(
        await screen.findByRole("heading", {
          name: "페이지를 찾을 수 없습니다",
        }),
      ).toBeInTheDocument();
    });
  });

  describe("내 순위 (R7)", () => {
    it("순위가 있는 회원은 표 위에 내 순위: N위 (M문제)를 본다", async () => {
      vi.mocked(getRankings).mockResolvedValue({
        entries: ENTRIES,
        me: { rank: 77, score: 5 },
      });

      await startRanking();

      const myRank = screen.getByText("내 순위: 77위 (5문제)");
      const table = screen.getByRole("table");
      expect(
        myRank.compareDocumentPosition(table) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).not.toBe(0);
      expect(screen.queryByText(NO_RANK)).not.toBeInTheDocument();
    });

    it("점수가 0인 회원 (rank null)은 아직 순위가 없어요를 본다", async () => {
      vi.mocked(getRankings).mockResolvedValue({
        entries: ENTRIES,
        me: { rank: null, score: 0 },
      });

      await startRanking();

      expect(screen.getByText(NO_RANK)).toBeInTheDocument();
      expect(screen.queryByText(/내 순위:/)).not.toBeInTheDocument();
    });

    it("비회원·관리자 (me null)는 내 순위 문구가 없다", async () => {
      await startRanking();

      expect(screen.queryByText(/내 순위/)).not.toBeInTheDocument();
      expect(screen.queryByText(NO_RANK)).not.toBeInTheDocument();
    });

    it("카테고리 랭킹의 내 순위는 그 카테고리 응답의 me다", async () => {
      vi.mocked(getRankings).mockResolvedValue({
        entries: ENTRIES,
        me: { rank: 77, score: 5 },
      });
      vi.mocked(getCategoryRankings).mockResolvedValue({
        entries: JAVA_RANKING.entries,
        me: { rank: 4, score: 2 },
      });

      await startRanking("/ranking?category=java");

      expect(screen.getByText("내 순위: 4위 (2문제)")).toBeInTheDocument();
      expect(screen.queryByText(/77위/)).not.toBeInTheDocument();
    });
  });

  describe("자기 줄 강조 (D9)", () => {
    it("50위 안이면 me.rank와 같은 등수의 줄 하나만 강조한다", async () => {
      vi.mocked(getRankings).mockResolvedValue({
        entries: ENTRIES,
        me: { rank: 2, score: 40 },
      });

      await startRanking();

      const highlighted = highlightedRows();
      expect(highlighted).toHaveLength(1);
      expect(highlighted[0]).toHaveTextContent("영희#1");
    });

    it("50위 밖이면 강조한 줄이 없다", async () => {
      vi.mocked(getRankings).mockResolvedValue({
        entries: ENTRIES,
        me: { rank: 77, score: 5 },
      });

      await startRanking();

      expect(highlightedRows()).toHaveLength(0);
    });

    it.each([
      ["비회원·관리자", null],
      ["순위 없는 회원", { rank: null, score: 0 }],
    ])("%s는 강조한 줄이 없다", async (_name, me) => {
      vi.mocked(getRankings).mockResolvedValue({ entries: ENTRIES, me });

      await startRanking();

      expect(highlightedRows()).toHaveLength(0);
    });
  });

  describe("빈 랭킹 (R10)", () => {
    it("목록이 비면 안내 문구를 보여 주고 표를 그리지 않는다", async () => {
      vi.mocked(getRankings).mockResolvedValue({ entries: [], me: null });

      await startRanking();

      expect(screen.getByText(EMPTY_MESSAGE)).toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    it("목록이 비어도 회원의 내 순위 문구와 카테고리 선택은 그대로 보인다", async () => {
      vi.mocked(getCategoryRankings).mockResolvedValue({
        entries: [],
        me: { rank: null, score: 0 },
      });

      await startRanking("/ranking?category=java");

      expect(screen.getByText(EMPTY_MESSAGE)).toBeInTheDocument();
      expect(screen.getByText(NO_RANK)).toBeInTheDocument();
      expect(categorySelect()).toHaveDisplayValue("자바");
    });
  });
});
