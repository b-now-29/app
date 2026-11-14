// 오답 노트 화면 (docs/features/wrong-answers.md R6, 화면 흐름).
// 구현할 파일: src/routes/WrongAnswersPage.tsx (default export), loader는 wrongAnswerLoaders.ts의 wrongAnswersLoader
// 카테고리마다 이름 (제목), 오답 수 ("N문제"), 문제 목록 (본문, 유형, 마지막으로 푼 날짜), /wrong-answers/:slug로 가는
// "다시 풀기" 링크를 보여 준다. 날짜는 모든 화면 공통 formatDate 형식
// ("YYYY.MM.DD", 브라우저 시간대, docs/features/ranking.md D13)이다.
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/http";
import { getWrongAnswers } from "../api/wrongAnswers";
import type {
  WrongAnswerCategoryResponse,
  WrongAnswerQuestionResponse,
} from "../api/wrongAnswers";
import ErrorPage from "./ErrorPage";
import WrongAnswersPage from "./WrongAnswersPage";
import { wrongAnswersLoader } from "./wrongAnswerLoaders";

vi.mock("../api/wrongAnswers", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/wrongAnswers")>()),
  getWrongAnswers: vi.fn(),
  getWrongAnswerQuestions: vi.fn(),
}));

const TITLE = "오답 노트";
const LOGIN_SCREEN = "로그인 화면";
const RETRY_SCREEN = "다시 풀기 화면";

const JAVA_ESSAY: WrongAnswerQuestionResponse = {
  questionId: 3,
  content: "자바 서술형 본문",
  type: "ESSAY",
  attemptedAt: "2026-11-06T01:23:45Z",
};
const JAVA_CHOICE: WrongAnswerQuestionResponse = {
  questionId: 1,
  content: "자바 객관식 본문",
  type: "MULTIPLE_CHOICE",
  attemptedAt: "2026-11-04T09:00:00Z",
};
const SPRING_SHORT: WrongAnswerQuestionResponse = {
  questionId: 7,
  content: "스프링 주관식 본문",
  type: "SHORT_ANSWER",
  attemptedAt: "2026-10-30T12:00:00Z",
};

const WRONG_ANSWERS: readonly WrongAnswerCategoryResponse[] = [
  { slug: "java", name: "자바", questions: [JAVA_ESSAY, JAVA_CHOICE] },
  { slug: "spring", name: "스프링", questions: [SPRING_SHORT] },
];

// 서울 시간대 (beforeEach에서 TZ 고정) 기준 마지막으로 푼 날짜
const DATES = new Map<WrongAnswerQuestionResponse, string>([
  [JAVA_ESSAY, "2026.11.06"],
  [JAVA_CHOICE, "2026.11.04"],
  [SPRING_SHORT, "2026.10.30"],
]);
const dateOf = (question: WrongAnswerQuestionResponse) => {
  const date = DATES.get(question);
  if (date === undefined) {
    throw new Error(`날짜 기대값이 없는 문제: ${question.questionId}`);
  }
  return date;
};

function LoginStub() {
  return <h1>{LOGIN_SCREEN}</h1>;
}

function RetryStub() {
  return <h1>{RETRY_SCREEN}</h1>;
}

function renderWrongAnswers() {
  const router = createMemoryRouter(
    [
      {
        path: "/wrong-answers",
        Component: WrongAnswersPage,
        loader: wrongAnswersLoader,
        ErrorBoundary: ErrorPage,
      },
      { path: "/wrong-answers/:slug", Component: RetryStub },
      { path: "/login", Component: LoginStub },
    ],
    { initialEntries: ["/wrong-answers"] },
  );
  render(<RouterProvider router={router} />);
  return { router, user: userEvent.setup() };
}

async function startWrongAnswers() {
  const rendered = renderWrongAnswers();
  await screen.findByRole("heading", { name: TITLE, level: 1 });
  return rendered;
}

const categoryHeading = (name: string) =>
  screen.getByRole("heading", { name: new RegExp(`^${name}`), level: 2 });
const retryLinks = () => screen.getAllByRole("link", { name: /다시 풀기/ });

const isBefore = (first: Element, second: Element) =>
  (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !==
  0;

// 같은 요소이거나, 기준 요소 안 또는 뒤에 있다
const isAtOrAfter = (element: Element, reference: Element) =>
  element === reference ||
  reference.contains(element) ||
  isBefore(reference, element);

beforeEach(() => {
  vi.stubEnv("TZ", "Asia/Seoul");
  vi.mocked(getWrongAnswers).mockReset();
  vi.mocked(getWrongAnswers).mockResolvedValue(WRONG_ANSWERS);
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("오답 노트 화면 (R6)", () => {
  describe("카테고리별 오답", () => {
    it("카테고리 이름을 서버가 준 순서대로 보여 준다", async () => {
      await startWrongAnswers();

      expect(isBefore(categoryHeading("자바"), categoryHeading("스프링"))).toBe(
        true,
      );
    });

    it("카테고리마다 오답 수를 보여 준다", async () => {
      await startWrongAnswers();

      const javaCount = screen.getByText(/2문제/);
      const springCount = screen.getByText(/1문제/);

      // 제목 안에 넣어도 되고 제목 뒤에 따로 두어도 된다. 다음 카테고리 제목보다는 앞이다
      expect(isAtOrAfter(javaCount, categoryHeading("자바"))).toBe(true);
      expect(isBefore(javaCount, categoryHeading("스프링"))).toBe(true);
      expect(isAtOrAfter(springCount, categoryHeading("스프링"))).toBe(true);
    });

    it("문제마다 본문, 유형, 마지막으로 푼 날짜를 서버가 준 순서대로 한 줄씩 보여 준다", async () => {
      await startWrongAnswers();

      const items = screen.getAllByRole("listitem");
      expect(items).toHaveLength(3);

      expect(items[0]).toHaveTextContent(JAVA_ESSAY.content);
      expect(items[0]).toHaveTextContent("서술형");
      expect(items[0]).toHaveTextContent(dateOf(JAVA_ESSAY));

      expect(items[1]).toHaveTextContent(JAVA_CHOICE.content);
      expect(items[1]).toHaveTextContent("객관식");
      expect(items[1]).toHaveTextContent(dateOf(JAVA_CHOICE));

      expect(items[2]).toHaveTextContent(SPRING_SHORT.content);
      expect(items[2]).toHaveTextContent("주관식");
      expect(items[2]).toHaveTextContent(dateOf(SPRING_SHORT));

      // 자바 문제는 자바와 스프링 제목 사이, 스프링 문제는 스프링 제목 뒤에 있다
      expect(isBefore(categoryHeading("자바"), items[0])).toBe(true);
      expect(isBefore(items[1], categoryHeading("스프링"))).toBe(true);
      expect(isBefore(categoryHeading("스프링"), items[2])).toBe(true);
    });

    it("정답, 모범 답안, 해설, 맞음 여부는 보여 주지 않는다 (R5)", async () => {
      await startWrongAnswers();

      expect(screen.queryByText(/모범 답안/)).not.toBeInTheDocument();
      expect(screen.queryByText(/해설/)).not.toBeInTheDocument();
      expect(screen.queryByText(/맞음|틀림/)).not.toBeInTheDocument();
    });
  });

  describe("다시 풀기 (D6)", () => {
    it("카테고리마다 그 카테고리의 다시 풀기 화면으로 가는 링크가 하나씩 있다", async () => {
      await startWrongAnswers();

      const links = retryLinks();
      expect(links).toHaveLength(2);
      expect(links[0]).toHaveAttribute("href", "/wrong-answers/java");
      expect(links[1]).toHaveAttribute("href", "/wrong-answers/spring");
      expect(isBefore(categoryHeading("자바"), links[0])).toBe(true);
      expect(isBefore(links[0], categoryHeading("스프링"))).toBe(true);
      expect(isBefore(categoryHeading("스프링"), links[1])).toBe(true);
    });

    it("다시 풀기 링크를 누르면 그 카테고리의 다시 풀기 화면으로 간다", async () => {
      const { router, user } = await startWrongAnswers();

      await user.click(retryLinks()[1]);

      expect(
        await screen.findByRole("heading", { name: RETRY_SCREEN }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/wrong-answers/spring");
    });
  });

  describe("오답이 없을 때", () => {
    it("안내 문구를 보여 주고 목록과 다시 풀기 링크를 그리지 않는다", async () => {
      vi.mocked(getWrongAnswers).mockResolvedValue([]);

      await startWrongAnswers();

      expect(screen.getByText(/오답이 없/)).toBeInTheDocument();
      expect(screen.queryAllByRole("listitem")).toHaveLength(0);
      expect(
        screen.queryByRole("link", { name: /다시 풀기/ }),
      ).not.toBeInTheDocument();
    });
  });

  describe("불러오기 실패", () => {
    it("비회원(401)이면 로그인 화면으로 보낸다 (R4)", async () => {
      vi.mocked(getWrongAnswers).mockRejectedValue(
        new ApiError(401, "로그인이 필요합니다."),
      );

      const { router } = renderWrongAnswers();

      expect(
        await screen.findByRole("heading", { name: LOGIN_SCREEN }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
    });

    it("그 밖의 실패는 에러 화면을 보여 준다", async () => {
      vi.mocked(getWrongAnswers).mockRejectedValue(
        new ApiError(500, "서버 오류"),
      );

      renderWrongAnswers();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("문제가 발생했습니다");
      expect(
        within(document.body).queryByRole("heading", { name: LOGIN_SCREEN }),
      ).not.toBeInTheDocument();
    });
  });
});
