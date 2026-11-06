import { redirect } from "react-router";
import { getAdminCategories } from "../api/admin";
import { ApiError } from "../api/users";

// 비회원(401)은 로그인 화면, USER(403)는 홈으로 보낸다. 그 밖의 실패는 에러 화면이 그리도록 다시 던진다.
export function rethrowAsRedirect(error: unknown): never {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      throw redirect("/login");
    }
    if (error.status === 403) {
      throw redirect("/");
    }
  }
  throw error;
}

export async function adminLoader() {
  try {
    return await getAdminCategories();
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}
