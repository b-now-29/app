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

export async function getJson<T>(url: string): Promise<T> {
  const response = await ensureOk(await fetch(url));
  return (await response.json()) as T;
}

function jsonRequest(method: "POST" | "PUT", body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export async function sendJson<T>(
  method: "POST" | "PUT",
  url: string,
  body: unknown,
): Promise<T> {
  const response = await ensureOk(await fetch(url, jsonRequest(method, body)));
  return (await response.json()) as T;
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
