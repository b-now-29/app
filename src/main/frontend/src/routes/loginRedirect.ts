import { redirect } from "react-router";
import { ApiError } from "../api/http";

// 비회원(401)이면 로그인 화면으로 보낸다. 그 밖의 실패는 에러 화면이 그리도록 그대로 다시 던진다.
export function rethrowWithLoginRedirect(error: unknown): never {
  if (error instanceof ApiError && error.status === 401) {
    throw redirect("/login");
  }
  throw error;
}
