import { useRouteLoaderData } from "react-router";
import type { AdminCategoryResponse } from "../api/admin";
import type { UserResponse } from "../api/users";
import type { adminLoader } from "./adminLoader";
import type { rootLoader } from "./rootLoader";

// 다른 route(부모)의 loader 결과를 읽는 hook. 그 route 아래가 아니면 데이터가 없으므로 Error를 던진다.

// "root" route의 현재 사용자. 비회원이면 null
export function useCurrentUser(): UserResponse | null {
  const user = useRouteLoaderData<typeof rootLoader>("root");
  if (user === undefined) {
    throw new Error('"root" route 아래에서만 현재 사용자를 읽을 수 있습니다');
  }
  return user;
}

// "admin" route의 카테고리 목록
export function useAdminCategories(): readonly AdminCategoryResponse[] {
  const categories = useRouteLoaderData<typeof adminLoader>("admin");
  if (categories === undefined) {
    throw new Error(
      '"admin" route 아래에서만 카테고리 목록을 읽을 수 있습니다',
    );
  }
  return categories;
}
