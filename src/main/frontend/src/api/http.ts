import type { z } from "zod";

// 서버가 준 상태 코드(401, 409 등)와 메시지를 화면까지 전달하기 위한 에러
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

// 없는 대상(404)과 서버 장애를 화면에서 다르게 보여 주기 위한 구분용 에러
export class NotFoundError extends ApiError {
  constructor(message: string) {
    super(404, message);
    this.name = "NotFoundError";
  }
}

// 실패 응답(ProblemDetail)의 detail을 메시지로 쓰고, 읽을 수 없으면 기본 문구를 쓴다
export async function toApiError(response: Response): Promise<ApiError> {
  const fallback = `요청에 실패했습니다. (HTTP ${response.status})`;
  const body: unknown = await response.json().catch(() => null);
  let message = fallback;
  if (typeof body === "object" && body !== null && "detail" in body) {
    const { detail } = body;
    if (typeof detail === "string" && detail !== "") {
      message = detail;
    }
  }
  return response.status === 404
    ? new NotFoundError(message)
    : new ApiError(response.status, message);
}

// 모든 요청 함수가 거치는 실패 처리.
// fetch는 404·500 응답에도 실패로 끝나지 않으므로 response.ok로 직접 확인한다.
// 비회원(401)을 로그인 화면으로 보내려면 상태 코드가 필요하므로,
// 실패는 상태 코드와 서버 메시지를 담은 ApiError(404는 NotFoundError)로 던진다.
async function ensureOk(response: Response): Promise<Response> {
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

// 응답은 200인데 본문 모양이 스키마와 다를 때의 에러.
// ApiError는 서버가 준 상태 코드를 뜻하므로 상속하지 않는다 (401·404 판단과 섞이지 않게).
export class ResponseFormatError extends Error {
  constructor(url: string, cause: unknown) {
    super(`응답 형식이 올바르지 않습니다. (${url})`, { cause });
    this.name = "ResponseFormatError";
  }
}

// 본문을 unknown으로 읽어 스키마로 검증하고, 통과한 값만 돌려준다
async function parseBody<S extends z.ZodType>(
  response: Response,
  url: string,
  schema: S,
): Promise<z.output<S>> {
  const body: unknown = await response.json();
  const result = schema.safeParse(body);
  if (!result.success) {
    throw new ResponseFormatError(url, result.error);
  }
  return result.data;
}

export async function getJson<S extends z.ZodType>(
  url: string,
  schema: S,
): Promise<z.output<S>> {
  const response = await ensureOk(await fetch(url));
  return parseBody(response, url, schema);
}

function jsonRequest(method: "POST" | "PUT", body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export async function sendJson<S extends z.ZodType>(
  method: "POST" | "PUT",
  url: string,
  body: unknown,
  schema: S,
): Promise<z.output<S>> {
  const response = await ensureOk(await fetch(url, jsonRequest(method, body)));
  return parseBody(response, url, schema);
}

// JSON 본문을 보내고 응답 본문은 읽지 않는다 (204)
export async function sendJsonForNoContent(
  method: "POST" | "PUT",
  url: string,
  body: unknown,
): Promise<void> {
  await ensureOk(await fetch(url, jsonRequest(method, body)));
}

// 본문 없이 보내고 응답 본문은 읽지 않는다
export async function sendWithoutBody(
  method: "POST" | "DELETE",
  url: string,
): Promise<void> {
  await ensureOk(await fetch(url, { method }));
}
