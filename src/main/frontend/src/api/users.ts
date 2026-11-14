import { z } from "zod";
import { AdminUserRoleSchema } from "./admin";
import {
  ApiError,
  getJson,
  sendJson,
  sendJsonForNoContent,
  sendWithoutBody,
} from "./http";

const UserSchema = z
  .object({
    id: z.number(),
    email: z.string(),
    role: AdminUserRoleSchema,
    nickname: z.string(),
    tag: z.number(),
  })
  .readonly();
export type UserResponse = z.infer<typeof UserSchema>;

export function signUp(email: string, password: string, nickname: string) {
  return sendJson(
    "POST",
    "/api/v1/users",
    { email, password, nickname },
    UserSchema,
  );
}

export function login(email: string, password: string) {
  return sendJson(
    "POST",
    "/api/v1/auth/login",
    { email, password },
    UserSchema,
  );
}

export function changeNickname(nickname: string) {
  return sendJson("PUT", "/api/v1/users/me/nickname", { nickname }, UserSchema);
}

export function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  return sendJsonForNoContent("PUT", "/api/v1/users/me/password", {
    currentPassword,
    newPassword,
  });
}

export function logout(): Promise<void> {
  return sendWithoutBody("POST", "/api/v1/auth/logout");
}

// 비회원(401)은 에러가 아니라 null이다
export async function getCurrentUser(): Promise<UserResponse | null> {
  try {
    return await getJson("/api/v1/users/me", UserSchema);
  } catch (thrown) {
    if (thrown instanceof ApiError && thrown.status === 401) {
      return null;
    }
    throw thrown;
  }
}
