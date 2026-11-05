import { getCurrentUser } from "../api/users";
import type { UserResponse } from "../api/users";

// 어떤 이유로 실패해도 비회원(null)으로 본다. 로그인 확인 장애 때문에 퀴즈 화면까지 막지 않기 위해서다.
export async function rootLoader(): Promise<UserResponse | null> {
  try {
    return await getCurrentUser();
  } catch {
    return null;
  }
}
