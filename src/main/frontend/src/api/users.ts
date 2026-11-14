export type UserResponse = {
  readonly id: number;
  readonly email: string;
  readonly role: "USER" | "ADMIN";
  readonly nickname: string;
  readonly tag: number;
};

// 서버가 준 상태 코드(401, 409 등)와 메시지를 화면까지 전달하기 위한 에러
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

// 화면에 보여 줄 에러 문구. 서버 메시지(ApiError)는 그대로, 그 밖은 기본 문구 (D22)
export function errorMessage(thrown: unknown): string {
  return thrown instanceof ApiError
    ? thrown.message
    : "요청에 실패했습니다. 다시 시도해 주세요.";
}

// 실패 응답(ProblemDetail)의 detail을 메시지로 쓰고, 읽을 수 없으면 기본 문구를 쓴다
export async function toApiError(response: Response): Promise<ApiError> {
  const fallback = `요청에 실패했습니다. (HTTP ${response.status})`;
  const body: unknown = await response.json().catch(() => null);
  if (typeof body === "object" && body !== null && "detail" in body) {
    const { detail } = body;
    if (typeof detail === "string" && detail !== "") {
      return new ApiError(response.status, detail);
    }
  }
  return new ApiError(response.status, fallback);
}

async function sendJson(
  url: string,
  method: "POST" | "PUT",
  body: object,
): Promise<UserResponse> {
  const response = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as UserResponse;
}

export function signUp(email: string, password: string, nickname: string) {
  return sendJson("/api/v1/users", "POST", { email, password, nickname });
}

export function login(email: string, password: string) {
  return sendJson("/api/v1/auth/login", "POST", { email, password });
}

export function changeNickname(nickname: string) {
  return sendJson("/api/v1/users/me/nickname", "PUT", { nickname });
}

export async function logout(): Promise<void> {
  const response = await fetch("/api/v1/auth/logout", { method: "POST" });
  if (!response.ok) {
    throw await toApiError(response);
  }
}

// 비회원(401)은 에러가 아니라 null이다
export async function getCurrentUser(): Promise<UserResponse | null> {
  const response = await fetch("/api/v1/users/me");
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as UserResponse;
}
