import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  getQuestions,
  NotFoundError,
  submitAnswer,
  submitTextAnswer,
} from "./questions";
import type {
  AnswerResponse,
  QuestionResponse,
  TextAnswerResponse,
} from "./questions";

const fetchMock = vi.spyOn(globalThis, "fetch");

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

function problemResponse(status: number): Response {
  return jsonResponse({ title: "실패", status }, status);
}

beforeEach(() => {
  fetchMock.mockReset();
});

describe("getQuestions", () => {
  it("카테고리의 문제 목록을 조회해 응답 본문을 돌려준다", async () => {
    const questions: readonly QuestionResponse[] = [
      {
        id: 1,
        type: "MULTIPLE_CHOICE",
        content: "문제 내용",
        choices: [{ id: 10, content: "보기 내용" }],
      },
      {
        id: 2,
        type: "SHORT_ANSWER",
        content: "주관식 문제 내용",
        choices: [],
      },
    ];
    fetchMock.mockResolvedValue(jsonResponse(questions));

    await expect(getQuestions("java")).resolves.toEqual(questions);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/categories/java/questions");
  });

  it("문제가 없는 카테고리는 빈 배열을 돌려준다 (R8)", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await expect(getQuestions("java")).resolves.toEqual([]);
  });

  it("404 응답은 NotFoundError로 던진다 (R8)", async () => {
    fetchMock.mockResolvedValue(problemResponse(404));

    await expect(getQuestions("nope")).rejects.toBeInstanceOf(NotFoundError);
  });

  it("500 응답은 NotFoundError가 아닌 Error로 던진다 (R8)", async () => {
    fetchMock.mockResolvedValue(problemResponse(500));

    const error: unknown = await getQuestions("java").catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(Error);
    expect(error).not.toBeInstanceOf(NotFoundError);
  });

  it.each([
    { slug: "../admin", encoded: "..%2Fadmin" },
    { slug: "a b/c?d#e", encoded: "a%20b%2Fc%3Fd%23e" },
  ])("slug $slug 값을 인코딩해서 호출한다", async ({ slug, encoded }) => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await getQuestions(slug);

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/categories/${encoded}/questions`,
    );
  });
});

describe("submitAnswer", () => {
  const answer: AnswerResponse = {
    correct: false,
    correctChoiceId: 11,
    explanation: null,
  };

  it("고른 보기를 해당 문제의 채점 주소에 JSON POST로 보낸다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(answer));

    await submitAnswer(7, 10);

    expect(fetchMock).toHaveBeenCalledWith("/api/v1/questions/7/answers", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ choiceId: 10 }),
    });
  });

  it("채점 응답 본문을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(answer));

    await expect(submitAnswer(7, 10)).resolves.toEqual(answer);
  });

  it.each([400, 404, 500])("%i 응답이면 예외를 던진다 (R7)", async (status) => {
    fetchMock.mockResolvedValue(problemResponse(status));

    await expect(submitAnswer(7, 10)).rejects.toThrow();
  });

  it("네트워크 실패면 예외를 던진다 (R7)", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(submitAnswer(7, 10)).rejects.toThrow();
  });
});

describe("submitTextAnswer", () => {
  const reveal: TextAnswerResponse = {
    modelAnswer: "모범 답안",
    explanation: null,
  };

  it("입력한 답을 그대로 해당 문제의 text-answers 주소에 JSON POST로 보낸다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(reveal));

    await submitTextAnswer(7, "  첫 줄\n둘째 줄  ");

    expect(fetchMock).toHaveBeenCalledWith("/api/v1/questions/7/text-answers", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ answer: "  첫 줄\n둘째 줄  " }),
    });
  });

  it("응답 본문을 돌려준다", async () => {
    fetchMock.mockResolvedValue(jsonResponse(reveal));

    await expect(submitTextAnswer(7, "답")).resolves.toEqual(reveal);
  });

  it.each([400, 404, 500])("%i 응답이면 예외를 던진다 (R7)", async (status) => {
    fetchMock.mockResolvedValue(problemResponse(status));

    await expect(submitTextAnswer(7, "답")).rejects.toThrow();
  });

  it("네트워크 실패면 예외를 던진다 (R7)", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(submitTextAnswer(7, "답")).rejects.toThrow();
  });
});
