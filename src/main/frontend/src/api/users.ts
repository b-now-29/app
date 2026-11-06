export type UserResponse = {
  readonly id: number;
  readonly email: string;
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

async function postCredentials(
  url: string,
  email: string,
  password: string,
): Promise<UserResponse> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as UserResponse;
}

export function signUp(email: string, password: string) {
  return postCredentials("/api/v1/users", email, password);
}

export function login(email: string, password: string) {
  return postCredentials("/api/v1/auth/login", email, password);
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
