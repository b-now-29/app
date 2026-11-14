// 오답 노트 API 호출 함수 (docs/features/wrong-answers.md API 계약).
// 구현할 파일: src/api/wrongAnswers.ts
//   export type WrongAnswerQuestionResponse = { questionId; content; type: QuestionType; attemptedAt: string }
//   export type WrongAnswerCategoryResponse = { slug; name; questions: readonly WrongAnswerQuestionResponse[] }
//   export function getWrongAnswers(): Promise<readonly WrongAnswerCategoryResponse[]>
//   export function getWrongAnswerQuestions(slug: string): Promise<readonly QuestionResponse[]>
// 401 등 실패는 상태 코드를 담은 ApiError로 던진다 (loader가 401이면 /login으로 보낸다).
// 다시 풀기용 문제의 404는 QuizErrorPage가 404 화면을 그리도록 기존 문제 API처럼 NotFoundError로 던진다.
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NotFoundError } from "./http";
import type { QuestionResponse } from "./questions";
import { getWrongAnswerQuestions, getWrongAnswers } from "./wrongAnswers";
import type { WrongAnswerCategoryResponse } from "./wrongAnswers";

const fetchMock = vi.spyOn(globalThis, "fetch");

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

function problemResponse(status: number): Response {
  return jsonResponse({ title: "실패", status }, status);
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

beforeEach(() => {
  fetchMock.mockReset();
});

describe("getWrongAnswers", () => {
  const wrongAnswers: readonly WrongAnswerCategoryResponse[] = [
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

  it("내 오답 목록을 조회해 응답 본문을 그대로 돌려준다 (R6)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(wrongAnswers));

    await expect(getWrongAnswers()).resolves.toEqual(wrongAnswers);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/wrong-answers");
  });

  it("오답이 없으면 빈 배열을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(getWrongAnswers()).resolves.toEqual([]);
  });

  it.each([401, 500])(
    "%i 응답이면 상태 코드를 담은 ApiError를 던진다 (R4)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status));

      const error = await caught(getWrongAnswers());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status });
    },
  );
});

describe("getWrongAnswerQuestions", () => {
  const questions: readonly QuestionResponse[] = [
    {
      id: 1,
      type: "MULTIPLE_CHOICE",
      content: "객관식 문제",
      choices: [
        { id: 10, content: "==" },
        { id: 11, content: "equals()" },
      ],
    },
  ];

  it("그 카테고리의 다시 풀기용 문제를 조회해 응답 본문을 그대로 돌려준다 (D4)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(questions));

    await expect(getWrongAnswerQuestions("java")).resolves.toEqual(questions);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/users/me/wrong-answers/java/questions",
    );
  });

  // slug는 주소창에서 온 값이므로 인코딩하지 않으면 "../"가 호출 경로를 바꿀 수 있다
  it("slug를 인코딩해 호출 경로를 바꿀 수 없게 한다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await getWrongAnswerQuestions("../admin");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/users/me/wrong-answers/..%2Fadmin/questions",
    );
  });

  it("오답이 없으면 빈 배열을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(getWrongAnswerQuestions("java")).resolves.toEqual([]);
  });

  it("없거나 삭제된 카테고리(404)면 NotFoundError를 던진다", async () => {
    fetchMock.mockResolvedValue(problemResponse(404));

    await expect(getWrongAnswerQuestions("nope")).rejects.toBeInstanceOf(
      NotFoundError,
    );
  });

  it.each([401, 500])(
    "%i 응답이면 상태 코드를 담은 ApiError를 던진다 (R4)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status));

      const error = await caught(getWrongAnswerQuestions("java"));

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status });
    },
  );
});
