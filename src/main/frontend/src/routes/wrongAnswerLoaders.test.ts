// 오답 노트 화면의 loader (docs/features/wrong-answers.md 화면 흐름).
// 구현할 파일: src/routes/wrongAnswerLoaders.ts
//   export async function wrongAnswersLoader(): Promise<readonly WrongAnswerCategoryResponse[]>
//     getWrongAnswers()의 결과를 그대로 돌려준다.
//   export async function wrongAnswerQuizLoader({ params }: LoaderFunctionArgs): Promise<readonly QuestionResponse[]>
//     getWrongAnswerQuestions(params.slug)의 결과를 그대로 돌려준다. QuizPage가 quizLoader와 같은 모양으로 쓴다 (D5).
// 둘 다 401이면 /login으로 보내고 (historyLoader와 같은 방식), 그 밖의 실패 (404 포함)는 ErrorBoundary가 그리도록 다시 던진다.
import type { LoaderFunctionArgs } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NotFoundError } from "../api/http";
import type { QuestionResponse } from "../api/questions";
import { getWrongAnswerQuestions, getWrongAnswers } from "../api/wrongAnswers";
import type { WrongAnswerCategoryResponse } from "../api/wrongAnswers";
import {
  wrongAnswerQuizLoader,
  wrongAnswersLoader,
} from "./wrongAnswerLoaders";

vi.mock("../api/wrongAnswers", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/wrongAnswers")>()),
  getWrongAnswers: vi.fn(),
  getWrongAnswerQuestions: vi.fn(),
}));

const WRONG_ANSWERS: readonly WrongAnswerCategoryResponse[] = [
  {
    slug: "java",
    name: "자바",
    questions: [
      {
        questionId: 3,
        content: "문제 본문",
        type: "ESSAY",
        attemptedAt: "2026-11-06T01:23:45Z",
      },
    ],
  },
];

const QUESTIONS: readonly QuestionResponse[] = [
  { id: 3, type: "ESSAY", content: "문제 본문", choices: [] },
];

function quizArgs(slug: string): LoaderFunctionArgs {
  return {
    request: new Request(`http://localhost/wrong-answers/${slug}`),
    params: { slug },
    context: undefined,
  } as unknown as LoaderFunctionArgs;
}

// redirect는 돌려줘도 던져도 React Router가 같은 이동으로 처리하므로 둘 다 받는다
async function settle(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

function expectRedirectToLogin(result: unknown) {
  expect(result).toBeInstanceOf(Response);
  const response = result as Response;
  expect(response.status).toBeGreaterThanOrEqual(300);
  expect(response.status).toBeLessThan(400);
  expect(response.headers.get("Location")).toBe("/login");
}

beforeEach(() => {
  vi.mocked(getWrongAnswers).mockReset();
  vi.mocked(getWrongAnswerQuestions).mockReset();
});

describe("wrongAnswersLoader", () => {
  it("내 오답 목록을 그대로 돌려준다 (R6)", async () => {
    vi.mocked(getWrongAnswers).mockResolvedValue(WRONG_ANSWERS);

    await expect(wrongAnswersLoader()).resolves.toEqual(WRONG_ANSWERS);
  });

  it("오답이 없으면 빈 목록을 돌려준다", async () => {
    vi.mocked(getWrongAnswers).mockResolvedValue([]);

    await expect(wrongAnswersLoader()).resolves.toEqual([]);
  });

  it("비회원(401)이면 /login으로 보낸다 (R4)", async () => {
    vi.mocked(getWrongAnswers).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectToLogin(await settle(wrongAnswersLoader()));
  });

  it.each([
    ["서버 오류", new ApiError(500, "서버 오류")],
    ["네트워크 오류", new TypeError("Failed to fetch")],
  ])("%s는 로그인 화면으로 보내지 않고 에러로 던진다", async (_name, error) => {
    vi.mocked(getWrongAnswers).mockRejectedValue(error);

    await expect(wrongAnswersLoader()).rejects.toBe(error);
  });
});

describe("wrongAnswerQuizLoader", () => {
  it("경로의 slug로 다시 풀기용 문제를 받아 그대로 돌려준다 (D4, D5)", async () => {
    vi.mocked(getWrongAnswerQuestions).mockResolvedValue(QUESTIONS);

    await expect(wrongAnswerQuizLoader(quizArgs("java"))).resolves.toEqual(
      QUESTIONS,
    );
    expect(getWrongAnswerQuestions).toHaveBeenCalledWith("java");
  });

  it("비회원(401)이면 /login으로 보낸다 (R4)", async () => {
    vi.mocked(getWrongAnswerQuestions).mockRejectedValue(
      new ApiError(401, "로그인이 필요합니다."),
    );

    expectRedirectToLogin(
      await settle(wrongAnswerQuizLoader(quizArgs("java"))),
    );
  });

  it("없거나 삭제된 카테고리(404)는 ErrorBoundary가 404 화면을 그리도록 NotFoundError를 그대로 던진다", async () => {
    const error = new NotFoundError("없는 카테고리: nope");
    vi.mocked(getWrongAnswerQuestions).mockRejectedValue(error);

    await expect(wrongAnswerQuizLoader(quizArgs("nope"))).rejects.toBe(error);
  });

  it("서버 오류는 로그인 화면으로 보내지 않고 에러로 던진다", async () => {
    const error = new ApiError(500, "서버 오류");
    vi.mocked(getWrongAnswerQuestions).mockRejectedValue(error);

    await expect(wrongAnswerQuizLoader(quizArgs("java"))).rejects.toBe(error);
  });
});
