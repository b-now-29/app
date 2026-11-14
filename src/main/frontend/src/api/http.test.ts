import { beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import {
  ApiError,
  getJson,
  NotFoundError,
  ResponseFormatError,
  sendJson,
  sendJsonForNoContent,
  sendWithoutBody,
  toApiError,
} from "./http";

const fetchMock = vi.spyOn(globalThis, "fetch");

const CategoriesSchema = z.array(z.object({ slug: z.string() }));
const CreatedSchema = z.object({ id: z.number() });

const JSON_HEADERS = { "Content-Type": "application/json" };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

function problemResponse(status: number, detail?: unknown): Response {
  return jsonResponse({ title: "실패", status, detail }, status);
}

async function caught(promise: Promise<unknown>): Promise<unknown> {
  return promise.catch((thrown: unknown) => thrown);
}

beforeEach(() => {
  fetchMock.mockReset();
});

describe("ApiError", () => {
  it("상태 코드와 메시지를 담은 Error다", () => {
    const error = new ApiError(409, "이미 있습니다.");

    expect(error).toBeInstanceOf(Error);
    expect(error).toMatchObject({
      name: "ApiError",
      status: 409,
      message: "이미 있습니다.",
    });
  });
});

describe("NotFoundError", () => {
  it("상태 404를 담은 ApiError다", () => {
    const error = new NotFoundError("없습니다.");

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toBeInstanceOf(Error);
    expect(error).toMatchObject({
      name: "NotFoundError",
      status: 404,
      message: "없습니다.",
    });
  });
});

describe("toApiError", () => {
  it("ProblemDetail의 detail을 메시지로, 응답 상태를 status로 쓴다", async () => {
    const error = await toApiError(
      problemResponse(409, "이미 있는 slug입니다."),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).not.toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({
      status: 409,
      message: "이미 있는 slug입니다.",
    });
  });

  it.each([
    ["detail이 없으면", problemResponse(500)],
    ["detail이 빈 문자열이면", problemResponse(500, "")],
    ["detail이 문자열이 아니면", problemResponse(500, 42)],
    [
      "본문이 JSON이 아니면",
      new Response("<html>오류</html>", { status: 500 }),
    ],
    ["본문이 비어 있으면", new Response(null, { status: 500 })],
    ["본문이 JSON null이면", jsonResponse(null, 500)],
  ])("%s 상태 코드를 담은 기본 문구를 쓴다", async (_case, response) => {
    const error = await toApiError(response);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 500,
      message: "요청에 실패했습니다. (HTTP 500)",
    });
  });

  it("404면 서버 메시지를 담은 NotFoundError다", async () => {
    const error = await toApiError(
      problemResponse(404, "없는 카테고리입니다."),
    );

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({
      status: 404,
      message: "없는 카테고리입니다.",
    });
  });

  it("detail이 없는 404도 기본 문구를 담은 NotFoundError다", async () => {
    const error = await toApiError(new Response(null, { status: 404 }));

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({
      status: 404,
      message: "요청에 실패했습니다. (HTTP 404)",
    });
  });
});

describe("getJson", () => {
  it("주소를 GET으로 조회해 스키마를 통과한 JSON 본문을 돌려준다 (R1)", async () => {
    fetchMock.mockResolvedValue(jsonResponse([{ slug: "java" }]));

    await expect(
      getJson("/api/v1/categories", CategoriesSchema),
    ).resolves.toEqual([{ slug: "java" }]);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/categories");
  });

  it.each([
    ["필드 타입이 다르면", [{ slug: 1 }]],
    ["필드가 없으면", [{}]],
    ["배열 자리에 객체가 오면", { slug: "java" }],
    ["본문이 null이면", null],
  ])("%s URL을 담은 ResponseFormatError를 던진다 (R2)", async (_case, body) => {
    fetchMock.mockResolvedValue(jsonResponse(body));

    const error = await caught(getJson("/api/v1/categories", CategoriesSchema));

    expect(error).toBeInstanceOf(ResponseFormatError);
    expect(error).toBeInstanceOf(Error);
    expect(error).not.toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      message: expect.stringContaining("/api/v1/categories"),
    });
  });

  it("스키마에 없는 필드는 실패하지 않고 결과에서 빠진다 (R3)", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([{ slug: "java", addedLater: "새 필드" }]),
    );

    await expect(
      getJson("/api/v1/categories", CategoriesSchema),
    ).resolves.toStrictEqual([{ slug: "java" }]);
  });

  it("실패 응답은 스키마 검증 전에 상태와 서버 메시지를 담은 ApiError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(401, "로그인이 필요합니다."));

    const error = await caught(
      getJson("/api/v1/users/me/attempts", CategoriesSchema),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 401,
      message: "로그인이 필요합니다.",
    });
  });

  it("404 응답은 NotFoundError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(404, "없습니다."));

    const error = await caught(
      getJson("/api/v1/admin/questions/1", CreatedSchema),
    );

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({ status: 404 });
  });

  it("네트워크 실패는 그대로 던진다", async () => {
    const failure = new TypeError("Failed to fetch");
    fetchMock.mockRejectedValue(failure);

    await expect(getJson("/api/v1/categories", CategoriesSchema)).rejects.toBe(
      failure,
    );
  });
});

describe("sendJson", () => {
  it.each(["POST", "PUT"] as const)(
    "%s로 JSON 헤더와 직렬화한 본문을 보내고 스키마를 통과한 JSON 응답 본문을 돌려준다 (R1)",
    async (method) => {
      const body = { name: "Java", description: null, sortOrder: 10 };
      fetchMock.mockResolvedValue(jsonResponse({ id: 1 }, 201));

      await expect(
        sendJson(method, "/api/v1/admin/categories", body, CreatedSchema),
      ).resolves.toEqual({ id: 1 });
      expect(fetchMock).toHaveBeenCalledWith("/api/v1/admin/categories", {
        method,
        headers: JSON_HEADERS,
        body: JSON.stringify(body),
      });
    },
  );

  it.each(["POST", "PUT"] as const)(
    "%s 응답 모양이 스키마와 다르면 URL을 담은 ResponseFormatError를 던진다 (R2)",
    async (method) => {
      fetchMock.mockResolvedValue(jsonResponse({ id: "1" }));

      const error = await caught(
        sendJson(method, "/api/v1/admin/categories", {}, CreatedSchema),
      );

      expect(error).toBeInstanceOf(ResponseFormatError);
      expect(error).toBeInstanceOf(Error);
      expect(error).not.toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        message: expect.stringContaining("/api/v1/admin/categories"),
      });
    },
  );

  it("스키마에 없는 필드는 실패하지 않고 결과에서 빠진다 (R3)", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ id: 1, addedLater: "새 필드" }, 201),
    );

    await expect(
      sendJson("POST", "/api/v1/admin/categories", {}, CreatedSchema),
    ).resolves.toStrictEqual({ id: 1 });
  });

  it("실패 응답은 상태와 서버 메시지를 담은 ApiError다", async () => {
    fetchMock.mockResolvedValue(
      problemResponse(400, "입력이 올바르지 않습니다."),
    );

    const error = await caught(
      sendJson("POST", "/api/v1/users", {}, CreatedSchema),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      message: "입력이 올바르지 않습니다.",
    });
  });

  it("404 응답은 NotFoundError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(404, "없습니다."));

    const error = await caught(
      sendJson("PUT", "/api/v1/admin/questions/1", {}, CreatedSchema),
    );

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({ status: 404 });
  });
});

describe("sendJsonForNoContent", () => {
  it.each(["POST", "PUT"] as const)(
    "%s로 JSON 헤더와 직렬화한 본문을 보내고 204 빈 응답이면 끝난다",
    async (method) => {
      const body = { correct: true };
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      await expect(
        sendJsonForNoContent(method, "/api/v1/questions/1/self-grades", body),
      ).resolves.toBeUndefined();
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/v1/questions/1/self-grades",
        {
          method,
          headers: JSON_HEADERS,
          body: JSON.stringify(body),
        },
      );
    },
  );

  it("성공 응답의 본문은 읽지 않는다", async () => {
    const response = new Response("JSON이 아닌 본문", { status: 200 });
    fetchMock.mockResolvedValue(response);

    await expect(
      sendJsonForNoContent("POST", "/api/v1/questions/1/self-grades", {
        correct: false,
      }),
    ).resolves.toBeUndefined();
    expect(response.bodyUsed).toBe(false);
  });

  it("실패 응답은 상태와 서버 메시지를 담은 ApiError다", async () => {
    fetchMock.mockResolvedValue(
      problemResponse(400, "입력이 올바르지 않습니다."),
    );

    const error = await caught(
      sendJsonForNoContent("POST", "/api/v1/questions/1/self-grades", {}),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).not.toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({
      status: 400,
      message: "입력이 올바르지 않습니다.",
    });
  });

  it("404 응답은 NotFoundError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(404, "없는 문제입니다."));

    const error = await caught(
      sendJsonForNoContent("POST", "/api/v1/questions/999/self-grades", {
        correct: true,
      }),
    );

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({ status: 404, message: "없는 문제입니다." });
  });
});

describe("sendWithoutBody", () => {
  it.each(["POST", "DELETE"] as const)(
    "%s를 본문 없이 보내고 204 빈 응답이면 끝난다",
    async (method) => {
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      await expect(
        sendWithoutBody(method, "/api/v1/auth/logout"),
      ).resolves.toBeUndefined();
      expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/logout", {
        method,
      });
    },
  );

  it("성공 응답의 본문은 읽지 않는다", async () => {
    const response = new Response("JSON이 아닌 본문", { status: 200 });
    fetchMock.mockResolvedValue(response);

    await expect(
      sendWithoutBody("POST", "/api/v1/auth/logout"),
    ).resolves.toBeUndefined();
    expect(response.bodyUsed).toBe(false);
  });

  it("실패 응답은 상태와 서버 메시지를 담은 ApiError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(409, "사용 중입니다."));

    const error = await caught(
      sendWithoutBody("DELETE", "/api/v1/admin/categories/java"),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, message: "사용 중입니다." });
  });

  it("404 응답은 NotFoundError다", async () => {
    fetchMock.mockResolvedValue(problemResponse(404, "없습니다."));

    const error = await caught(
      sendWithoutBody("DELETE", "/api/v1/admin/questions/1"),
    );

    expect(error).toBeInstanceOf(NotFoundError);
    expect(error).toMatchObject({ status: 404 });
  });
});
