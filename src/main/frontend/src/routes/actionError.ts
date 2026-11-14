import { ApiError } from "../api/http";

export type ActionError = { readonly error: string };

const DEFAULT_MESSAGE = "요청에 실패했습니다. 다시 시도해 주세요.";

// 화면에 보여 줄 에러 문구. 서버 메시지(ApiError)는 그대로, 그 밖은 fallback (D22)
export function errorMessage(
  thrown: unknown,
  fallback: string = DEFAULT_MESSAGE,
): string {
  return thrown instanceof ApiError ? thrown.message : fallback;
}

// action에서 잡은 실패를 화면에 보일 오류 문구로 바꾼다.
// 서버가 준 메시지 (ApiError)는 그대로, 그 밖의 실패는 호출한 쪽이 정한 문구를 쓴다.
export function toActionError(thrown: unknown, fallback: string): ActionError {
  return { error: errorMessage(thrown, fallback) };
}
