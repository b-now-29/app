import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, NotFoundError, ResponseFormatError } from "./http";
import {
  getQuestions,
  submitAnswer,
  submitSelfGrade,
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

function problemResponse(status: number, detail?: string): Response {
  return jsonResponse({ title: "실패", status, detail }, status);
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
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

  it("응답 모양이 다르면 ResponseFormatError를 던진다 (api-response-validation R2)", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([
        { id: 1, type: "TRUE_FALSE", content: "문제 내용", choices: [] },
      ]),
    );

    await expect(getQuestions("java")).rejects.toBeInstanceOf(
      ResponseFormatError,
    );
  });

  it("404 응답은 NotFoundError로 던진다 (R8)", async () => {
    fetchMock.mockResolvedValue(problemResponse(404));

    const error = await caught(getQuestions("nope"));

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({ status: 404 });
  });

  it("500 응답은 NotFoundError가 아닌, 상태와 서버 메시지를 담은 ApiError로 던진다 (R8)", async () => {
    fetchMock.mockResolvedValue(problemResponse(500, "서버 오류"));

    const error = await caught(getQuestions("java"));

    expect(error).toBeInstanceOf(ApiError);
    expect(error).not.toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({ status: 500, message: "서버 오류" });
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
    recorded: false,
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

  it("기록 저장 여부 recorded를 그대로 돌려준다 (D13)", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ...answer, recorded: true }));

    await expect(submitAnswer(7, 10)).resolves.toMatchObject({
      recorded: true,
    });
  });

  it.each([400, 404, 500])(
    "%i 응답이면 상태와 서버 메시지를 담은 ApiError를 던진다 (R7)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status, "채점 실패 이유"));

      const error = await caught(submitAnswer(7, 10));

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, message: "채점 실패 이유" });
    },
  );

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

  it.each([400, 404, 500])(
    "%i 응답이면 상태와 서버 메시지를 담은 ApiError를 던진다 (R7)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status, "제출 실패 이유"));

      const error = await caught(submitTextAnswer(7, "답"));

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status, message: "제출 실패 이유" });
    },
  );

  it("404 응답은 NotFoundError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(404));

    await expect(submitTextAnswer(7, "답")).rejects.toBeInstanceOf(
      NotFoundError,
    );
  });

  it("네트워크 실패면 예외를 던진다 (R7)", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(submitTextAnswer(7, "답")).rejects.toThrow();
  });
});

describe("submitSelfGrade", () => {
  it.each([true, false])(
    "자기 채점 결과 %s를 해당 문제의 self-grades 주소에 JSON POST로 보낸다 (R2)",
    async (correct) => {
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      await submitSelfGrade(7, correct);

      expect(fetchMock).toHaveBeenCalledWith(
        "/api/v1/questions/7/self-grades",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ correct }),
        },
      );
    },
  );

  it("204 응답이면 본문 없이 끝난다", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(submitSelfGrade(7, true)).resolves.toBeUndefined();
  });

  // 화면이 401(로그인 만료)과 그 밖의 실패를 다르게 처리하므로 상태 코드를 담아 던진다 (D9)
  it.each([400, 401, 404, 500])(
    "%i 응답이면 상태 코드를 담은 ApiError를 던진다 (D9)",
    async (status) => {
      fetchMock.mockResolvedValue(problemResponse(status));

      const error: unknown = await submitSelfGrade(7, true).catch(
        (thrown: unknown) => thrown,
      );

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({ status });
    },
  );

  it("네트워크 실패면 예외를 던진다 (D9)", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(submitSelfGrade(7, true)).rejects.toThrow();
  });
});
