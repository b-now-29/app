import { redirect } from "react-router";
import { getAdminCategories } from "../api/admin";
import { ApiError } from "../api/http";
import { rethrowWithLoginRedirect } from "./loginRedirect";

// USER(403)는 홈으로 보낸다. 비회원(401)과 그 밖의 실패는 rethrowWithLoginRedirect가 맡는다.
export function rethrowAsRedirect(error: unknown): never {
  if (error instanceof ApiError && error.status === 403) {
    throw redirect("/");
  }
  return rethrowWithLoginRedirect(error);
}

export async function adminLoader() {
  try {
    return await getAdminCategories();
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}
