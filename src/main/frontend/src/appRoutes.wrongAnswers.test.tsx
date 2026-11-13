// 오답 노트 route와 메뉴 (docs/features/wrong-answers.md 화면 흐름, D5).
// appRoutes에 추가할 route (둘 다 skipAfterLogout을 붙이지 않는다):
//   /wrong-answers       → WrongAnswersPage, loader: wrongAnswersLoader
//   /wrong-answers/:slug → QuizPage 재사용, loader: wrongAnswerQuizLoader. 결과 화면 (와 빈 화면)의 링크는
//                          "카테고리 목록" 대신 /wrong-answers로 가는 "오답 노트"다.
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { UserEvent } from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCategories } from "./api/categories";
import {
  getQuestions,
  NotFoundError,
  submitAnswer,
  submitSelfGrade,
  submitTextAnswer,
} from "./api/questions";
import type { QuestionResponse } from "./api/questions";
import { ApiError, getCurrentUser, logout } from "./api/users";
import type { UserResponse } from "./api/users";
import { getWrongAnswerQuestions, getWrongAnswers } from "./api/wrongAnswers";
import type { WrongAnswerCategoryResponse } from "./api/wrongAnswers";
import { appRoutes } from "./appRoutes";

vi.mock("./api/categories");

// 에러 화면이 NotFoundError를 instanceof로 구분하므로 클래스는 진짜를 쓴다
vi.mock("./api/questions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/questions")>()),
  getQuestions: vi.fn(),
  submitAnswer: vi.fn(),
  submitTextAnswer: vi.fn(),
  submitSelfGrade: vi.fn(),
}));

vi.mock("./api/wrongAnswers", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/wrongAnswers")>()),
  getWrongAnswers: vi.fn(),
  getWrongAnswerQuestions: vi.fn(),
}));

// 모듈 전체를 자동으로 대체하면 ApiError도 가짜가 되어 status와 message가 비게 된다
vi.mock("./api/users", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/users")>()),
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  signUp: vi.fn(),
}));

const USER: UserResponse = { id: 1, email: "user@example.com", role: "USER" };
const MENU_LINK = "오답 노트";
const TITLE = "오답 노트";
const CATEGORY_NAME = "자바";
const LIST_LINK = "카테고리 목록";
const SCORE_TITLE = "풀이 결과";
const EMPTY_QUIZ_TITLE = "아직 문제가 없습니다";

const WRONG_CHOICE: QuestionResponse = {
  id: 101,
  type: "MULTIPLE_CHOICE",
  content: "틀렸던 객관식",
  choices: [
    { id: 11, content: "가방" },
    { id: 12, content: "라면" },
  ],
};
const WRONG_ESSAY: QuestionResponse = {
  id: 102,
  type: "ESSAY",
  content: "틀렸던 서술형",
  choices: [],
};

// 서버의 상태를 흉내 낸다. 쿠키 (currentUser)와 오답으로 남은 문제 id
let currentUser: UserResponse | null;
let wrongQuestionIds: Set<number>;

const ALL_QUESTIONS = [WRONG_CHOICE, WRONG_ESSAY];

function wrongAnswersNow(): readonly WrongAnswerCategoryResponse[] {
  const questions = ALL_QUESTIONS.filter((question) =>
    wrongQuestionIds.has(question.id),
  ).map((question) => ({
    questionId: question.id,
    content: question.content,
    type: question.type,
    attemptedAt: "2026-11-06T01:23:45Z",
  }));
  return questions.length === 0
    ? []
    : [{ slug: "java", name: CATEGORY_NAME, questions }];
}

const unauthorized = () => new ApiError(401, "로그인이 필요합니다.");

beforeEach(() => {
  vi.resetAllMocks();
  currentUser = USER;
  wrongQuestionIds = new Set([WRONG_CHOICE.id, WRONG_ESSAY.id]);
  vi.mocked(getCategories).mockResolvedValue([
    { slug: "java", name: CATEGORY_NAME },
  ]);
  vi.mocked(getQuestions).mockResolvedValue(ALL_QUESTIONS);
  vi.mocked(getCurrentUser).mockImplementation(() =>
    Promise.resolve(currentUser),
  );
  vi.mocked(logout).mockImplementation(() => {
    currentUser = null;
    return Promise.resolve();
  });
  // 오답 API는 인증 필수다. 비회원이면 서버처럼 401로 거절한다 (R4)
  vi.mocked(getWrongAnswers).mockImplementation(() =>
    currentUser === null
      ? Promise.reject(unauthorized())
      : Promise.resolve(wrongAnswersNow()),
  );
  vi.mocked(getWrongAnswerQuestions).mockImplementation((slug) => {
    if (currentUser === null) return Promise.reject(unauthorized());
    if (slug !== "java")
      return Promise.reject(new NotFoundError(`없는 카테고리: ${slug}`));
    return Promise.resolve(
      ALL_QUESTIONS.filter((question) => wrongQuestionIds.has(question.id)),
    );
  });
  // 다시 풀기도 기존 저장 API를 쓴다. 맞히면 오답에서 빠진다 (R2)
  vi.mocked(submitAnswer).mockImplementation((questionId, choiceId) => {
    const correct = choiceId === 11;
    if (correct) wrongQuestionIds.delete(questionId);
    return Promise.resolve({
      correct,
      correctChoiceId: 11,
      explanation: null,
      recorded: true,
    });
  });
  vi.mocked(submitTextAnswer).mockResolvedValue({
    modelAnswer: "모범 답안",
    explanation: null,
  });
  vi.mocked(submitSelfGrade).mockImplementation((questionId, correct) => {
    if (correct) wrongQuestionIds.delete(questionId);
    return Promise.resolve();
  });
});

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return { router, user: userEvent.setup() };
}

async function findMenu() {
  return within(await screen.findByRole("navigation"));
}

// 회원 메뉴에도 "오답 노트" 링크가 있으므로 화면 본문 안에서만 찾는다
const main = () => within(screen.getByRole("main"));

async function expectWrongAnswersScreen() {
  expect(
    await screen.findByRole("heading", { name: TITLE, level: 1 }),
  ).toBeInTheDocument();
}

async function expectLoginScreen() {
  expect(await screen.findByLabelText("비밀번호")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
}

// 객관식은 맞히고 서술형은 자기 채점으로 틀렸다고 해 결과 화면까지 간다
async function solveRetry(user: UserEvent) {
  await user.click(await screen.findByRole("radio", { name: "가방" }));
  await user.click(screen.getByRole("button", { name: "제출" }));
  await user.click(await screen.findByRole("button", { name: "다음 문제" }));
  await user.type(
    await screen.findByRole("textbox", { name: "내 답" }),
    "내 답안",
  );
  await user.click(screen.getByRole("button", { name: "제출" }));
  await user.click(await screen.findByRole("button", { name: "틀렸어요" }));
  await screen.findByRole("heading", { name: SCORE_TITLE });
}

describe("오답 노트 메뉴", () => {
  it("회원 메뉴에 /wrong-answers로 가는 오답 노트 링크가 있고 내 기록 링크 뒤에 있다", async () => {
    renderApp("/");

    const menu = await findMenu();
    const link = await menu.findByRole("link", { name: MENU_LINK });
    expect(link).toHaveAttribute("href", "/wrong-answers");
    const history = menu.getByRole("link", { name: "내 기록" });
    expect(
      history.compareDocumentPosition(link) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).not.toBe(0);
  });

  it("비회원 메뉴에는 오답 노트 링크가 없다", async () => {
    currentUser = null;
    renderApp("/");

    const menu = await findMenu();
    await menu.findByRole("link", { name: "로그인" });
    expect(
      menu.queryByRole("link", { name: MENU_LINK }),
    ).not.toBeInTheDocument();
  });

  it("메뉴의 오답 노트 링크를 누르면 오답 노트 화면으로 간다", async () => {
    const { user } = renderApp("/");

    const menu = await findMenu();
    await user.click(await menu.findByRole("link", { name: MENU_LINK }));

    await expectWrongAnswersScreen();
    expect(main().getByText("틀렸던 객관식")).toBeInTheDocument();
  });
});

describe("다시 풀기 (D5, R7)", () => {
  it("오답 노트의 다시 풀기 링크로 그 카테고리의 오답 문제만 푸는 화면에 간다", async () => {
    const { router, user } = renderApp("/wrong-answers");
    await expectWrongAnswersScreen();

    await user.click(main().getByRole("link", { name: /다시 풀기/ }));

    expect(
      await screen.findByRole("heading", { name: WRONG_CHOICE.content }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/wrong-answers/java");
    expect(screen.getByText("문제 1 / 2")).toBeInTheDocument();
    expect(getWrongAnswerQuestions).toHaveBeenCalledWith("java");
    // 카테고리 전체 문제 API는 쓰지 않는다 (D4)
    expect(getQuestions).not.toHaveBeenCalled();
  });

  it("기존 풀이 화면처럼 채점하고 자기 채점을 저장한다", async () => {
    const { user } = renderApp("/wrong-answers/java");

    await solveRetry(user);

    expect(submitAnswer).toHaveBeenCalledWith(WRONG_CHOICE.id, 11);
    expect(submitSelfGrade).toHaveBeenCalledWith(WRONG_ESSAY.id, false);
    expect(
      screen.getByText("객관식 1문제 중 정답 1문제, 오답 0문제"),
    ).toBeInTheDocument();
  });

  it("결과 화면의 링크는 카테고리 목록이 아니라 /wrong-answers로 가는 오답 노트다", async () => {
    const { user } = renderApp("/wrong-answers/java");

    await solveRetry(user);

    expect(main().getByRole("link", { name: TITLE })).toHaveAttribute(
      "href",
      "/wrong-answers",
    );
    expect(
      main().queryByRole("link", { name: LIST_LINK }),
    ).not.toBeInTheDocument();
  });

  it("결과 화면에서 오답 노트로 돌아오면 목록을 다시 받아 맞힌 문제가 빠지고 틀린 문제는 남는다 (R2)", async () => {
    const { router, user } = renderApp("/wrong-answers/java");
    await solveRetry(user);

    await user.click(main().getByRole("link", { name: TITLE }));

    await expectWrongAnswersScreen();
    expect(router.state.location.pathname).toBe("/wrong-answers");
    expect(main().getByText(WRONG_ESSAY.content)).toBeInTheDocument();
    expect(main().queryByText(WRONG_CHOICE.content)).not.toBeInTheDocument();
  });

  it("다시 풀 오답이 없으면 빈 화면의 링크도 오답 노트로 간다", async () => {
    wrongQuestionIds = new Set();

    renderApp("/wrong-answers/java");

    // loader가 끝나야 <main>이 생기므로 먼저 기다린다
    const body = within(await screen.findByRole("main"));
    expect(await body.findByRole("link", { name: TITLE })).toHaveAttribute(
      "href",
      "/wrong-answers",
    );
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(
      main().queryByRole("link", { name: LIST_LINK }),
    ).not.toBeInTheDocument();
  });

  // (코드 리뷰) 다시 풀기 빈 화면은 "문제가 없다"가 아니라 남은 오답이 없다고 알린다
  it("다시 풀 오답이 없으면 빈 화면 제목은 아직 문제가 없다가 아니라 남은 오답이 없다는 문구다", async () => {
    wrongQuestionIds = new Set();

    renderApp("/wrong-answers/java");

    const body = within(await screen.findByRole("main"));
    const title = await body.findByRole("heading", { level: 1 });
    expect(title).toHaveTextContent(/오답이 없/);
    expect(
      body.queryByRole("heading", { name: EMPTY_QUIZ_TITLE }),
    ).not.toBeInTheDocument();
  });

  it("카테고리에서 푸는 기존 풀이 화면의 빈 화면 문구와 링크는 그대로다", async () => {
    vi.mocked(getQuestions).mockResolvedValue([]);

    renderApp("/categories/java");

    const body = within(await screen.findByRole("main"));
    expect(
      await body.findByRole("heading", { name: EMPTY_QUIZ_TITLE, level: 1 }),
    ).toBeInTheDocument();
    expect(body.getByRole("link", { name: LIST_LINK })).toHaveAttribute(
      "href",
      "/",
    );
  });

  it("없거나 삭제된 카테고리면 404 화면을 보여 준다", async () => {
    renderApp("/wrong-answers/nope");

    expect(
      await screen.findByRole("heading", { name: "페이지를 찾을 수 없습니다" }),
    ).toBeInTheDocument();
  });

  it("카테고리에서 푸는 기존 풀이 화면의 결과 링크는 그대로 카테고리 목록이다", async () => {
    vi.mocked(getQuestions).mockResolvedValue([WRONG_CHOICE]);
    const { user } = renderApp("/categories/java");

    await user.click(await screen.findByRole("radio", { name: "가방" }));
    await user.click(screen.getByRole("button", { name: "제출" }));
    await user.click(await screen.findByRole("button", { name: "결과 보기" }));
    await screen.findByRole("heading", { name: SCORE_TITLE });

    expect(main().getByRole("link", { name: LIST_LINK })).toHaveAttribute(
      "href",
      "/",
    );
    expect(main().queryByRole("link", { name: TITLE })).not.toBeInTheDocument();
  });
});

describe("비회원 접근 (R4)", () => {
  it.each(["/wrong-answers", "/wrong-answers/java"])(
    "비회원이 %s에 오면 로그인 화면으로 보낸다",
    async (path) => {
      currentUser = null;

      const { router } = renderApp(path);

      await expectLoginScreen();
      expect(router.state.location.pathname).toBe("/login");
    },
  );

  // 두 route 모두 skipAfterLogout을 붙이지 않아 loader가 다시 실행되고 401로 로그인 화면에 간다
  it("오답 노트 화면에서 로그아웃하면 로그인 화면으로 간다", async () => {
    const { user } = renderApp("/wrong-answers");
    await expectWrongAnswersScreen();

    const menu = await findMenu();
    await user.click(menu.getByRole("button", { name: "로그아웃" }));

    await expectLoginScreen();
  });

  it("다시 풀기 화면에서 로그아웃하면 로그인 화면으로 간다", async () => {
    const { user } = renderApp("/wrong-answers/java");
    await screen.findByRole("heading", { name: WRONG_CHOICE.content });

    const menu = await findMenu();
    await user.click(menu.getByRole("button", { name: "로그아웃" }));

    await expectLoginScreen();
  });
});
