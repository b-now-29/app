import { redirect } from "react-router";
import type { LoaderFunctionArgs } from "react-router";
import { isOwner } from "../user/role";
import { getAdminCategories, getAdminUser, getAdminUsers } from "../api/admin";
import { rethrowAsRedirect } from "./adminLoader";

export async function adminUsersLoader() {
  try {
    return await getAdminUsers();
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}

export async function adminUserLoader({ params }: LoaderFunctionArgs) {
  try {
    const [user, categories] = await Promise.all([
      getAdminUser(Number(params.id)),
      getAdminCategories(),
    ]);
    // 운영자의 역할은 바꿀 수 없으므로 상세 화면 대신 목록으로 보낸다
    if (isOwner(user.role)) {
      return redirect("/admin/users");
    }
    return { user, categories };
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}
