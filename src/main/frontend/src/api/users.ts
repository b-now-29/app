import { ApiError, getJson, sendJson, sendWithoutBody } from "./http";

export type UserResponse = {
  readonly id: number;
  readonly email: string;
  readonly role: "USER" | "ADMIN";
  readonly nickname: string;
  readonly tag: number;
};

export function signUp(email: string, password: string, nickname: string) {
  return sendJson<UserResponse>("POST", "/api/v1/users", {
    email,
    password,
    nickname,
  });
}

export function login(email: string, password: string) {
  return sendJson<UserResponse>("POST", "/api/v1/auth/login", {
    email,
    password,
  });
}

export function changeNickname(nickname: string) {
  return sendJson<UserResponse>("PUT", "/api/v1/users/me/nickname", {
    nickname,
  });
}

export function logout(): Promise<void> {
  return sendWithoutBody("POST", "/api/v1/auth/logout");
}

// 비회원(401)은 에러가 아니라 null이다
export async function getCurrentUser(): Promise<UserResponse | null> {
  try {
    return await getJson<UserResponse>("/api/v1/users/me");
  } catch (thrown) {
    if (thrown instanceof ApiError && thrown.status === 401) {
      return null;
    }
    throw thrown;
  }
}
