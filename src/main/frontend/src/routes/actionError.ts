import { ApiError } from "../api/users";

export type ActionError = { readonly error: string };

// action에서 잡은 실패를 화면에 보일 오류 문구로 바꾼다.
// 서버가 준 메시지 (ApiError)는 그대로, 그 밖의 실패는 기본 문구를 쓴다.
export function toActionError(thrown: unknown, fallback: string): ActionError {
  return { error: thrown instanceof ApiError ? thrown.message : fallback };
}
